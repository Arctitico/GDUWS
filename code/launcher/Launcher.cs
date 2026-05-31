// GDUWS 原生启动器 - 由 build.ps1 用 csc.exe 编译为 GDUWS.exe
// 作用：定位同目录下的内置 JRE 与 GDUWS.jar，以自身所在目录为工作目录启动游戏
// 这样游戏内 data/ 与 assets/ 等相对路径资源可被正确解析
using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows.Forms;

internal static class GduwsLauncher
{
    [STAThread]
    private static int Main(string[] args)
    {
        string baseDir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
        string javaw = Path.Combine(baseDir, "runtime", "bin", "javaw.exe");
        string jar = Path.Combine(baseDir, "GDUWS.jar");

        if (!File.Exists(javaw))
        {
            MessageBox.Show("缺少内置运行环境：" + javaw, "GDUWS 启动失败",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
        if (!File.Exists(jar))
        {
            MessageBox.Show("缺少游戏程序：" + jar, "GDUWS 启动失败",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }

        var psi = new ProcessStartInfo
        {
            FileName = javaw,
            Arguments = "-jar \"" + jar + "\"",
            WorkingDirectory = baseDir,
            UseShellExecute = false
        };

        try
        {
            using (var p = Process.Start(psi))
            {
                p.WaitForExit();
                return p.ExitCode;
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show("启动游戏时出错：" + ex.Message, "GDUWS 启动失败",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }
}
