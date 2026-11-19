using System;
using System.Runtime.InteropServices;

// 媒体控制：只广播一次 WM_APPCOMMAND（重复发送会导致播放器连跳多首）
// 用法：MediaKey.exe play|next|prev|stop
class MediaKey
{
    const uint WM_APPCOMMAND = 0x0319;
    const int APPCOMMAND_MEDIA_NEXTTRACK = 11;
    const int APPCOMMAND_MEDIA_PREVIOUSTRACK = 12;
    const int APPCOMMAND_MEDIA_STOP = 13;
    const int APPCOMMAND_MEDIA_PLAY_PAUSE = 14;

    static readonly IntPtr HWND_BROADCAST = new IntPtr(0xFFFF);
    const uint SMTO_ABORTIFHUNG = 0x0002;

    [DllImport("user32.dll", SetLastError = true)]
    static extern IntPtr SendMessageTimeout(IntPtr hWnd, uint Msg, IntPtr wParam,
        IntPtr lParam, uint fuFlags, uint uTimeout, out IntPtr lpdwResult);

    [DllImport("user32.dll")]
    static extern IntPtr GetForegroundWindow();

    static int Main(string[] args)
    {
        if (args.Length < 1) return 2;

        string a = args[0].ToLower();
        int cmd;
        switch (a)
        {
            case "next": cmd = APPCOMMAND_MEDIA_NEXTTRACK; break;
            case "prev": cmd = APPCOMMAND_MEDIA_PREVIOUSTRACK; break;
            case "play": cmd = APPCOMMAND_MEDIA_PLAY_PAUSE; break;
            case "stop": cmd = APPCOMMAND_MEDIA_STOP; break;
            default: return 3;
        }

        try
        {
            IntPtr result;
            IntPtr lParam = new IntPtr(cmd << 16);

            // 只发一次：优先给前台窗口；没有前台窗口时才广播
            IntPtr fg = GetForegroundWindow();
            IntPtr target = fg != IntPtr.Zero ? fg : HWND_BROADCAST;

            SendMessageTimeout(target, WM_APPCOMMAND, IntPtr.Zero, lParam,
                SMTO_ABORTIFHUNG, 300, out result);

            Console.WriteLine("ok=" + a);
            return 0;
        }
        catch (Exception e)
        {
            Console.WriteLine("error=" + e.Message);
            return 1;
        }
    }
}
