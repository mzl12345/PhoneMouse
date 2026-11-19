using System;
using System.Runtime.InteropServices;
using System.Text;

// 用 SendInput 的 Unicode 模式输入文本，绕开输入法候选词劫持
// 用法: TypeText.exe <base64 的 UTF-8 文本>
class TypeText
{
    [StructLayout(LayoutKind.Sequential)]
    struct MOUSEINPUT
    {
        public int dx;
        public int dy;
        public uint mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    struct KEYBDINPUT
    {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    struct HARDWAREINPUT
    {
        public uint uMsg;
        public ushort wParamL;
        public ushort wParamH;
    }

    [StructLayout(LayoutKind.Explicit)]
    struct InputUnion
    {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
        [FieldOffset(0)] public HARDWAREINPUT hi;
    }

    [StructLayout(LayoutKind.Sequential)]
    struct INPUT
    {
        public uint type;
        public InputUnion u;
    }

    [DllImport("user32.dll", SetLastError = true)]
    static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    const uint INPUT_KEYBOARD = 1;
    const uint KEYEVENTF_KEYUP = 0x0002;
    const uint KEYEVENTF_UNICODE = 0x0004;
    const ushort VK_RETURN = 0x0D;

    static void Main(string[] args)
    {
        if (args.Length < 1) return;
        string text;
        try
        {
            text = Encoding.UTF8.GetString(Convert.FromBase64String(args[0]));
        }
        catch
        {
            text = args[0];
        }

        foreach (char c in text)
        {
            if (c == '\r') continue;
            if (c == '\n')
            {
                Key(VK_RETURN, false);
                Key(VK_RETURN, true);
                continue;
            }
            Uni(c);
            System.Threading.Thread.Sleep(3);
        }
    }

    static void Uni(char c)
    {
        INPUT[] a = new INPUT[1];
        a[0].type = INPUT_KEYBOARD;
        a[0].u.ki.wVk = 0;
        a[0].u.ki.wScan = (ushort)c;
        a[0].u.ki.dwFlags = KEYEVENTF_UNICODE;
        SendInput(1, a, Marshal.SizeOf(typeof(INPUT)));

        a[0].u.ki.dwFlags = KEYEVENTF_UNICODE | KEYEVENTF_KEYUP;
        SendInput(1, a, Marshal.SizeOf(typeof(INPUT)));
    }

    static void Key(ushort vk, bool up)
    {
        INPUT[] a = new INPUT[1];
        a[0].type = INPUT_KEYBOARD;
        a[0].u.ki.wVk = vk;
        a[0].u.ki.wScan = 0;
        a[0].u.ki.dwFlags = up ? KEYEVENTF_KEYUP : 0;
        SendInput(1, a, Marshal.SizeOf(typeof(INPUT)));
    }
}
