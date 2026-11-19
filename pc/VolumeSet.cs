using System;
using System.Runtime.InteropServices;

// 直接调用 Windows Core Audio API 读写主音量，不依赖按键注入。
// 用法：
//   VolumeSet.exe info          打印当前音量
//   VolumeSet.exe up [步进]     音量+
//   VolumeSet.exe down [步进]   音量-
//   VolumeSet.exe mute          切换静音
class VolumeSet
{
    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    class MMDeviceEnumeratorComObject { }

    [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDeviceEnumerator
    {
        int NotImpl1();
        int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice ppDevice);
    }

    [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDevice
    {
        int Activate(ref Guid iid, int dwClsCtx, IntPtr pActivationParams,
            [MarshalAs(UnmanagedType.IUnknown)] out object ppInterface);
    }

    [ComImport, Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioEndpointVolume
    {
        int RegisterControlChangeNotify(IntPtr p);
        int UnregisterControlChangeNotify(IntPtr p);
        int GetChannelCount(out int c);
        int SetMasterVolumeLevel(float level, IntPtr ctx);
        int SetMasterVolumeLevelScalar(float level, IntPtr ctx);
        int GetMasterVolumeLevel(out float level);
        int GetMasterVolumeLevelScalar(out float level);
        int SetChannelVolumeLevel(int ch, float level, IntPtr ctx);
        int SetChannelVolumeLevelScalar(int ch, float level, IntPtr ctx);
        int GetChannelVolumeLevel(int ch, out float level);
        int GetChannelVolumeLevelScalar(int ch, out float level);
        int SetMute([MarshalAs(UnmanagedType.Bool)] bool mute, IntPtr ctx);
        int GetMute([MarshalAs(UnmanagedType.Bool)] out bool mute);
    }

    static IAudioEndpointVolume GetEndpoint()
    {
        var enumerator = (IMMDeviceEnumerator)(new MMDeviceEnumeratorComObject());
        IMMDevice device;
        enumerator.GetDefaultAudioEndpoint(0, 1, out device);   // eRender, eMultimedia
        Guid iid = typeof(IAudioEndpointVolume).GUID;
        object o;
        device.Activate(ref iid, 23, IntPtr.Zero, out o);      // CLSCTX_ALL
        return (IAudioEndpointVolume)o;
    }

    static int Main(string[] args)
    {
        try
        {
            var vol = GetEndpoint();
            if (args.Length == 0 || args[0] == "info")
            {
                float lvl;
                vol.GetMasterVolumeLevelScalar(out lvl);
                bool mute;
                vol.GetMute(out mute);
                Console.WriteLine("level=" + (int)Math.Round(lvl * 100) + " mute=" + mute);
                return 0;
            }

            float cur;
            vol.GetMasterVolumeLevelScalar(out cur);

            switch (args[0])
            {
                case "up":
                case "down":
                {
                    float step = args.Length > 1 ? float.Parse(args[1]) / 100f : 0.02f;
                    float next = args[0] == "up" ? cur + step : cur - step;
                    if (next > 1f) next = 1f;
                    if (next < 0f) next = 0f;
                    vol.SetMasterVolumeLevelScalar(next, IntPtr.Zero);
                    Console.WriteLine("level=" + (int)Math.Round(next * 100));
                    return 0;
                }
                case "mute":
                {
                    bool mute;
                    vol.GetMute(out mute);
                    vol.SetMute(!mute, IntPtr.Zero);
                    Console.WriteLine("mute=" + !mute);
                    return 0;
                }
                case "set":
                {
                    float v = float.Parse(args[1]) / 100f;
                    vol.SetMasterVolumeLevelScalar(Math.Max(0f, Math.Min(1f, v)), IntPtr.Zero);
                    Console.WriteLine("level=" + (int)Math.Round(v * 100));
                    return 0;
                }
            }
            return 2;
        }
        catch (Exception e)
        {
            Console.WriteLine("error=" + e.Message);
            return 1;
        }
    }
}
