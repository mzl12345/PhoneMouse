using System;
using System.IO.Ports;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

// 蓝牙串口桥：把手机的蓝牙串口数据转发到本机 UDP 51234
// 由电脑端主程序自动拉起，不需要用户手动运行
class BtBridge
{
    static readonly IPEndPoint Target = new IPEndPoint(IPAddress.Loopback, 51234);
    static readonly UdpClient Udp = new UdpClient();

    static void Main(string[] args)
    {
        int port = 51234;
        if (args.Length > 0)
        {
            int.TryParse(args[0], out port);
        }
        var target = new IPEndPoint(IPAddress.Loopback, port);

        while (true)
        {
            string[] ports = SerialPort.GetPortNames();
            foreach (string p in ports)
            {
                RunPort(p, target);
            }
            Thread.Sleep(3000);
        }
    }

    static void RunPort(string port, IPEndPoint target)
    {
        SerialPort sp = null;
        try
        {
            sp = new SerialPort(port, 115200, Parity.None, 8, StopBits.One);
            sp.ReadTimeout = 400;
            sp.WriteTimeout = 400;
            sp.Open();
            Console.WriteLine("BT bridge connected: " + port);

            var sb = new StringBuilder();
            var buf = new byte[2048];
            while (true)
            {
                int n;
                try
                {
                    n = sp.Read(buf, 0, buf.Length);
                }
                catch (TimeoutException)
                {
                    if (!sp.IsOpen) return;
                    continue;
                }
                if (n <= 0) continue;
                sb.Append(Encoding.UTF8.GetString(buf, 0, n));
                while (true)
                {
                    string all = sb.ToString();
                    int idx = all.IndexOf('\n');
                    if (idx < 0) break;
                    string line = all.Substring(0, idx).Trim();
                    sb.Remove(0, idx + 1);
                    if (line.Length > 0)
                    {
                        byte[] data = Encoding.UTF8.GetBytes(line);
                        Udp.Send(data, data.Length, target);
                    }
                }
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine("BT bridge " + port + " error: " + ex.Message);
        }
        finally
        {
            if (sp != null)
            {
                try { sp.Close(); } catch { }
            }
        }
    }
}
