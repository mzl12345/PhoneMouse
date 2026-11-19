using System;
using System.Runtime.InteropServices;

// 按键注入工具（SendInput）
//
// 用法一（虚拟键码）：
//   KeySend.exe vk <键码十进制> [扩展键1/0] [按住毫秒]
//   例：KeySend.exe vk 27 0 120
//
// 用法二（扫描码，最接近真实键盘）：
//   KeySend.exe scan <扫描码十六进制> [按住毫秒]
//   例：KeySend.exe scan 30 40     音量+
//        KeySend.exe scan 2E 40     音量-
//        KeySend.exe scan 22 40     播放/暂停
//        KeySend.exe scan 19 40     下一曲
//        KeySend.exe scan 10 40     上一曲
class KeySend
{
    [StructLayout(LayoutKind.Sequential)]
    struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Explicit)]
    struct INPUT
    {
        [FieldOffset(0)] public uint type;
        [FieldOffset(8)] public KEYBDINPUT ki;
    }

    [DllImport("user32.dll", SetLastError = true)]
    static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    const uint INPUT_KEYBOARD = 1;
    const uint KEYEVENTF_EXTENDEDKEY = 0x0001;
    const uint KEYEVENTF_KEYUP = 0x0002;
    const uint KEYEVENTF_SCANCODE = 0x0008;

    static void Send(ushort vk, ushort scan, uint flags)
    {
        INPUT[] inputs = new INPUT[1];
        inputs[0].type = INPUT_KEYBOARD;
        inputs[0].ki.wVk = vk;
        inputs[0].ki.wScan = scan;
        inputs[0].ki.dwFlags = flags;
        inputs[0].ki.time = 0;
        inputs[0].ki.dwExtraInfo = IntPtr.Zero;
        SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT)));
    }

    static int Main(string[] args)
    {
        if (args.Length < 2) return 2;

        if (args[0] == "scan")
        {
            ushort scan;
            if (!ushort.TryParse(args[1], System.Globalization.NumberStyles.HexNumber, null, out scan))
                return 3;
            int hold = args.Length > 2 ? int.Parse(args[2]) : 40;

            Send(0, scan, KEYEVENTF_SCANCODE | KEYEVENTF_EXTENDEDKEY);
            System.Threading.Thread.Sleep(hold);
            Send(0, scan, KEYEVENTF_SCANCODE | KEYEVENTF_EXTENDEDKEY | KEYEVENTF_KEYUP);
            return 0;
        }

        if (args[0] == "vk")
        {
            ushort vk;
            if (!ushort.TryParse(args[1], out vk)) return 3;
            bool extended = args.Length > 2 && args[2] == "1";
            int hold = args.Length > 3 ? int.Parse(args[3]) : 40;

            Send(vk, 0, extended ? KEYEVENTF_EXTENDEDKEY : 0);
            System.Threading.Thread.Sleep(hold);
            Send(vk, 0, (extended ? KEYEVENTF_EXTENDEDKEY : 0) | KEYEVENTF_KEYUP);
            return 0;
        }

        return 4;
    }
}
