// Instalador del parche "Español (España)" para Pokémon Fire Ash (PC).
// WinForms sobre .NET Framework 4 (incluido en Windows 10/11): un solo .exe, sin dependencias.
// Los archivos del parche van en la carpeta "parche" junto al .exe.
using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.RegularExpressions;
using System.Windows.Forms;

[assembly: System.Reflection.AssemblyTitle("Fire Ash - Español (España)")]
[assembly: System.Reflection.AssemblyProduct("Ash Droid Launcher - parche PC")]

static class Program
{
    [STAThread]
    static void Main()
    {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);
        Application.Run(new InstallerForm());
    }
}

class InstallerForm : Form
{
    const string PATCH = "ashdroid_patches.rb";
    const string MARK = "ashdroid_pc_version.txt";
    static readonly Color BG = Color.FromArgb(26, 29, 35), PANEL = Color.FromArgb(36, 40, 48),
        FG = Color.FromArgb(235, 235, 240), DIM = Color.FromArgb(150, 156, 168),
        ORANGE = Color.FromArgb(255, 138, 0), GREEN = Color.FromArgb(46, 160, 67), RED = Color.FromArgb(220, 80, 70);

    readonly string payload = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "parche");
    readonly TextBox folder = new TextBox();
    readonly Label status = new Label();
    readonly Button install = new Button(), uninstall = new Button(), play = new Button();
    string version = "?";

    public InstallerForm()
    {
        try { version = File.ReadAllText(Path.Combine(payload, "VERSION.txt")).Trim(); } catch { }
        Text = "Fire Ash · Español (España)";
        try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
        FormBorderStyle = FormBorderStyle.FixedSingle;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;
        ClientSize = new Size(620, 360);
        BackColor = BG;
        ForeColor = FG;
        Font = new Font("Segoe UI", 10f);

        var title = new Label { Text = "Pokémon Fire Ash", Font = new Font("Segoe UI", 20f, FontStyle.Bold),
            AutoSize = true, Location = new Point(24, 18) };
        var sub = new Label { Text = "Parche Español (España) · v" + version, ForeColor = ORANGE,
            Font = new Font("Segoe UI", 11f, FontStyle.Bold), AutoSize = true, Location = new Point(27, 60) };
        var desc = new Label { Text = "Retraducción completa al castellano de España, con los nombres oficiales, gráficos en español " +
            "y la intro en castellano. No modifica los archivos del juego ni tus partidas.",
            ForeColor = DIM, Location = new Point(27, 88), Size = new Size(570, 40) };

        var lf = new Label { Text = "CARPETA DEL JUEGO", ForeColor = DIM, Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
            AutoSize = true, Location = new Point(27, 138) };
        folder.Location = new Point(27, 160); folder.Size = new Size(452, 28);
        folder.BackColor = PANEL; folder.ForeColor = FG; folder.BorderStyle = BorderStyle.FixedSingle;
        folder.TextChanged += (s, e) => Refresh2();
        var browse = Btn("Examinar…", PANEL, new Point(489, 158), new Size(104, 30));
        browse.Click += (s, e) => Browse();

        status.Location = new Point(27, 198); status.Size = new Size(570, 44); status.ForeColor = DIM;

        var hint = new Label { Text = "La intro suena en el idioma del juego (español, francés o alemán). Se puede quitar en Opciones del juego.",
            ForeColor = DIM, Location = new Point(27, 246), Size = new Size(570, 40) };

        install.Text = "Instalar"; Style(install, GREEN, new Point(27, 292), new Size(180, 44));
        install.Font = new Font("Segoe UI", 12f, FontStyle.Bold);
        install.Click += (s, e) => Run(Install);
        uninstall.Text = "Desinstalar"; Style(uninstall, PANEL, new Point(217, 292), new Size(140, 44));
        uninstall.Click += (s, e) => Run(Uninstall);
        play.Text = "▶ Jugar"; Style(play, PANEL, new Point(453, 292), new Size(140, 44));
        play.Click += (s, e) => Play();

        Controls.AddRange(new Control[] { title, sub, desc, lf, folder, browse, status, hint, install, uninstall, play });
        folder.Text = Detect() ?? "";
        Refresh2();
    }

    Button Btn(string text, Color bg, Point p, Size sz) { var b = new Button { Text = text }; Style(b, bg, p, sz); return b; }

    void Style(Button b, Color bg, Point p, Size sz)
    {
        b.Location = p; b.Size = sz; b.BackColor = bg; b.ForeColor = Color.White;
        b.FlatStyle = FlatStyle.Flat; b.FlatAppearance.BorderSize = 0; b.Cursor = Cursors.Hand;
    }

    static bool IsGame(string d) => !string.IsNullOrEmpty(d) && Directory.Exists(d) && File.Exists(Path.Combine(d, "mkxp.json"));

    static string Detect()
    {
        var here = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\');
        foreach (var d in new[] { here, Path.GetDirectoryName(here) }) if (IsGame(d)) return d;
        var home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        var roots = new[] { Path.Combine(home, "Downloads"), Path.Combine(home, "Desktop"),
            Path.Combine(home, "Documents"), Path.Combine(home, "Games"), @"C:\Games" };
        foreach (var r in roots)
        {
            try
            {
                if (!Directory.Exists(r)) continue;
                foreach (var d in Directory.GetDirectories(r, "*Fire*Ash*"))
                {
                    if (IsGame(d)) return d;
                    foreach (var d2 in Directory.GetDirectories(d)) if (IsGame(d2)) return d2;
                }
            }
            catch { }
        }
        return null;
    }

    void Browse()
    {
        using (var dlg = new FolderBrowserDialog { Description = "Elige la carpeta de Pokémon Fire Ash (donde está Game.exe y mkxp.json)" })
        {
            if (IsGame(folder.Text)) dlg.SelectedPath = folder.Text;
            if (dlg.ShowDialog(this) == DialogResult.OK) folder.Text = dlg.SelectedPath;
        }
    }

    void Refresh2()
    {
        var d = folder.Text.Trim();
        bool ok = IsGame(d), installed = ok && Installed(d);
        install.Enabled = ok && Directory.Exists(payload);
        uninstall.Enabled = installed;
        play.Enabled = ok && File.Exists(Path.Combine(d, "Game.exe"));
        install.Text = installed ? "Actualizar" : "Instalar";
        if (!Directory.Exists(payload)) { Status("Falta la carpeta \"parche\" junto a este programa. Descomprime el zip entero.", RED); return; }
        if (string.IsNullOrEmpty(d)) { Status("Elige la carpeta del juego con \"Examinar…\".", DIM); return; }
        if (!ok) { Status("Esa carpeta no parece el juego: no tiene mkxp.json.", RED); return; }
        if (installed)
        {
            string v = "";
            try { v = File.ReadAllText(Path.Combine(d, MARK)).Trim(); } catch { }
            Status("Parche instalado" + (v.Length > 0 ? " (v" + v + ")" : "") +
                ". Con el juego en español (\"Spanish (Spain)\") ya se ve la nueva traducción.", GREEN);
        }
        else Status("Juego encontrado. El parche aún no está instalado.", FG);
    }

    void Status(string s, Color c) { status.Text = s; status.ForeColor = c; }

    static bool Installed(string d)
    {
        try { return File.ReadAllText(Path.Combine(d, "mkxp.json")).Contains(PATCH); } catch { return false; }
    }

    void Run(Action<string> a)
    {
        var d = folder.Text.Trim();
        try
        {
            Cursor = Cursors.WaitCursor;
            a(d);
        }
        catch (Exception ex)
        {
            MessageBox.Show(this, ex.Message, "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
        finally
        {
            Cursor = Cursors.Default;
            Refresh2();
        }
    }

    void Install(string d)
    {
        foreach (var src in Directory.GetFiles(payload, "*", SearchOption.AllDirectories))
        {
            var rel = src.Substring(payload.Length).TrimStart('\\', '/');
            if (rel == "VERSION.txt") continue;
            var dst = Path.Combine(d, rel);
            Directory.CreateDirectory(Path.GetDirectoryName(dst));
            File.Copy(src, dst, true);
        }

        var conf = Path.Combine(d, "mkxp.json");
        var t = File.ReadAllText(conf);
        if (!t.Contains(PATCH))
        {
            var bak = conf + ".bak";
            if (!File.Exists(bak)) File.Copy(conf, bak);
            var re = new Regex("(?m)^(\\s*\"preloadScript\"\\s*:\\s*\\[)");
            if (re.IsMatch(t)) t = re.Replace(t, "$1\"" + PATCH + "\", ", 1);
            else
            {
                int i = t.IndexOf('{');
                if (i < 0) throw new Exception("mkxp.json no tiene el formato esperado.");
                t = t.Substring(0, i + 1) + Environment.NewLine + "  \"preloadScript\": [\"" + PATCH + "\"]," + t.Substring(i + 1);
            }
            File.WriteAllText(conf, t, new UTF8Encoding(false));
        }
        File.WriteAllText(Path.Combine(d, MARK), version);
        MessageBox.Show(this, "¡Parche instalado!\n\nSi juegas en español, ya lo tienes: el idioma \"Spanish\" pasa a ser \"Spanish (Spain)\". Si no, elígelo en la pantalla de carga (opción Language).",
            "Fire Ash · Español (España)", MessageBoxButtons.OK, MessageBoxIcon.Information);
    }

    void Uninstall(string d)
    {
        var conf = Path.Combine(d, "mkxp.json");
        var t = File.ReadAllText(conf);
        t = Regex.Replace(t, "(?m)^\\s*\"preloadScript\"\\s*:\\s*\\[\\s*\"" + Regex.Escape(PATCH) + "\"\\s*\\],?\\r?\\n", "");
        t = Regex.Replace(t, "\"" + Regex.Escape(PATCH) + "\",\\s*", "");
        t = Regex.Replace(t, ",\\s*\"" + Regex.Escape(PATCH) + "\"", "");
        File.WriteAllText(conf, t, new UTF8Encoding(false));
        foreach (var f in Directory.GetFiles(d, "ashdroid_*")) File.Delete(f);
        foreach (var f in Directory.GetDirectories(d, "ashdroid_*")) Directory.Delete(f, true);
        MessageBox.Show(this, "Parche desinstalado. El juego vuelve a estar como antes.", "Fire Ash · Español (España)",
            MessageBoxButtons.OK, MessageBoxIcon.Information);
    }

    void Play()
    {
        var d = folder.Text.Trim();
        try { Process.Start(new ProcessStartInfo(Path.Combine(d, "Game.exe")) { WorkingDirectory = d }); Close(); }
        catch (Exception ex) { MessageBox.Show(this, ex.Message, "Error", MessageBoxButtons.OK, MessageBoxIcon.Error); }
    }
}
