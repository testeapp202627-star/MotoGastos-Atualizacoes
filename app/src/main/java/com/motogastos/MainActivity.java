package com.motogastos;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.content.*;
import android.net.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.text.*;

public class MainActivity extends Activity {
    LinearLayout tela;
    EditText kmCampo, litrosCampo, precoCampo, kmOleoCampo, valorOleoCampo;
    LinearLayout painel;
    TextView historicoView;
    android.content.SharedPreferences prefs;
    android.media.ToneGenerator som;
    long downloadAtualizacao = -1;
    android.content.BroadcastReceiver receptorDownload = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(i.getAction())) {
                long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadAtualizacao) instalarAtualizacao(id);
            }
        }
    };
    final int VERDE = Color.rgb(22,125,69);
    final int TANQUE = 14;
    final int INTERVALO_OLEO = 1000;

    String dinheiro(double v) {
        return String.format(new Locale("pt","BR"), "R$ %.2f", v);
    }

    double numero(EditText e) {
        String s = e.getText().toString().trim().replace(",", ".");
        if (s.isEmpty()) return 0;
        return Double.parseDouble(s);
    }

    String agora() {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm",
            Locale.getDefault()).format(new Date());
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("MotoGastosNovo", MODE_PRIVATE);
        som = new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100);

        ScrollView scroll = new ScrollView(this);
        tela = new LinearLayout(this);
        tela.setOrientation(1);
        tela.setPadding(22,20,22,30);
        tela.setBackgroundColor(Color.rgb(220,242,225));
        scroll.addView(tela);

        TextView titulo = new TextView(this);
        titulo.setText("🏍️ MotoGastos");
        titulo.setTextSize(30);
        titulo.setTextColor(VERDE);
        titulo.setGravity(Gravity.CENTER);
        titulo.setTypeface(null,Typeface.BOLD);
        tela.addView(titulo);

        TextView subtitulo = new TextView(this);
        subtitulo.setText("Controle de combustível e manutenção");
        subtitulo.setTextSize(15);
        subtitulo.setGravity(Gravity.CENTER);
        subtitulo.setPadding(0,8,0,20);
        tela.addView(subtitulo);

        painel = new LinearLayout(this);
        painel.setOrientation(1);
        painel.setBackgroundColor(Color.TRANSPARENT);
        tela.addView(painel);

        kmCampo = new EditText(this);
        litrosCampo = new EditText(this);
        precoCampo = new EditText(this);
        kmOleoCampo = new EditText(this);
        valorOleoCampo = new EditText(this);
        precoCampo.setText("6,80");
        valorOleoCampo.setText("55,00");
        historicoView = texto();

        botao("⛽ COMBUSTÍVEL", v -> menuCombustivel());
        botao("🔧 MANUTENÇÃO", v -> menuManutencao());
        botao("📋 RELATÓRIOS", v -> menuRelatorios());
        botao("⚙️ DADOS", v -> menuDados());

        setContentView(scroll);
        configurarNotificacoes();
        atualizar(); verificarAtualizacao(true);
    }

    void configurarNotificacoes() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel canal = new NotificationChannel(
                "motogastos_oleo",
                "Avisos de troca de óleo",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager gerenciador =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            gerenciador.createNotificationChannel(canal);
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{"android.permission.POST_NOTIFICATIONS"}, 1001);
        }
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 && grantResults.length > 0 &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            verificarAvisoOleo();
        }
    }

    void verificarAvisoOleo() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (!prefs.contains("kmOleo")) {
            return;
        }

        double km = prefs.getFloat("km", 0);
        double kmOleo = prefs.getFloat("kmOleo", 0);
        double faltam = kmOleo + INTERVALO_OLEO - km;

        if (faltam <= 0 &&
            !prefs.getBoolean("avisoOleoVencido", false)) {
            enviarAvisoOleo(3,
                "Troca de óleo vencida! Registre a troca no MotoGastos.");
            prefs.edit()
                .putBoolean("avisoOleoVencido", true)
                .putBoolean("avisoOleo50", true)
                .putBoolean("avisoOleo100", true)
                .apply();
        } else if (faltam <= 50 &&
            !prefs.getBoolean("avisoOleo50", false)) {
            enviarAvisoOleo(2,
                "Faltam no máximo 50 km para a troca de óleo.");
            prefs.edit()
                .putBoolean("avisoOleo50", true)
                .putBoolean("avisoOleo100", true)
                .apply();
        } else if (faltam <= 100 &&
            !prefs.getBoolean("avisoOleo100", false)) {
            enviarAvisoOleo(1,
                "Faltam 100 km ou menos para trocar o óleo.");
            prefs.edit()
                .putBoolean("avisoOleo100", true)
                .apply();
        }
    }

    void enviarAvisoOleo(int id, String mensagem) {
        Notification.Builder aviso;

        if (Build.VERSION.SDK_INT >= 26) {
            aviso = new Notification.Builder(this, "motogastos_oleo");
        } else {
            aviso = new Notification.Builder(this);
        }

        aviso.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MotoGastos - Troca de óleo")
            .setContentText(mensagem)
            .setStyle(new Notification.BigTextStyle().bigText(mensagem))
            .setAutoCancel(true);

        NotificationManager gerenciador =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        gerenciador.notify(id, aviso.build());
    }

    void tocarSom() {
        if (som != null) {
            som.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 250);
        }
    }

    @Override
    protected void onDestroy() {
        if (som != null) {
            som.release();
            som = null;
        }
        super.onDestroy();
    }

    void secao(String nome) {
        TextView t = texto();
        t.setText(nome);
        t.setTextColor(VERDE);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextSize(19);
        tela.addView(t);
    }

    TextView texto() {
        TextView t = new TextView(this);
        t.setTextColor(Color.DKGRAY);
        t.setTextSize(15);
        t.setPadding(12,12,12,12);
        t.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,6,0,10);
        t.setLayoutParams(p);
        return t;
    }

    EditText campo(String dica) {
        EditText e = new EditText(this);
        e.setHint(dica);
        e.setSingleLine(true);
        e.setInputType(8194);
        tela.addView(e);
        return e;
    }

    void botao(String nome, android.view.View.OnClickListener acao) {
        Button b = new Button(this);
        b.setText(nome);

        int cor = nome.contains("COMBUSTÍVEL") ? Color.rgb(22,125,69)
            : nome.contains("MANUTENÇÃO") ? Color.rgb(35,105,170)
            : nome.contains("RELATÓRIOS") ? Color.rgb(120,85,165)
            : Color.rgb(75,90,100);

        android.graphics.drawable.GradientDrawable fundo =
            new android.graphics.drawable.GradientDrawable();
        fundo.setColor(cor);
        fundo.setCornerRadius(22);
        b.setBackground(fundo);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setTypeface(null, Typeface.BOLD);
        b.setAllCaps(false);

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(4,7,4,7);
        b.setLayoutParams(p);

        tela.addView(b);
        b.setOnClickListener(v -> {
            tocarSom();
            acao.onClick(v);
        });
    }

    EditText campoDialog(String dica) {
        EditText e = new EditText(this);
        e.setHint(dica);
        e.setSingleLine(true);
        e.setInputType(8194);
        return e;
    }

    void menuCombustivel() {
        new AlertDialog.Builder(this)
            .setTitle("⛽ COMBUSTÍVEL")
            .setItems(new String[]{"Novo abastecimento", "Histórico"}, (d, n) -> { tocarSom();
                if (n == 0) formularioAbastecimento();
                else escolherRegistro();
            }).show();
    }

    void formularioAbastecimento() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(1);
        f.setPadding(24, 8, 24, 8);
        kmCampo = campoDialog("KM atual da moto");
        litrosCampo = campoDialog("Litros abastecidos");
        precoCampo = campoDialog("Preço por litro (R$)");
        precoCampo.setText("6,80");
        f.addView(kmCampo);
        f.addView(litrosCampo);
        f.addView(precoCampo);
        new AlertDialog.Builder(this)
            .setTitle("Novo abastecimento")
            .setView(f)
            .setNegativeButton("Cancelar", (d, w) -> tocarSom())
             .setPositiveButton("Salvar", (d, n) -> {
                tocarSom();
                abastecer();
            })
            .show();
    }

    void menuManutencao() {
        new AlertDialog.Builder(this)
            .setTitle("🔧 MANUTENÇÃO")
            .setItems(new String[]{"Registrar troca de óleo", "Histórico"}, (d, n) -> { tocarSom(); tocarSom();
                if (n == 0) formularioOleo();
                else escolherRegistro();
            }).show();
    }

    void formularioOleo() {
        LinearLayout f = new LinearLayout(this);
        f.setOrientation(1);
        f.setPadding(24, 8, 24, 8);
        kmOleoCampo = campoDialog("KM atual na troca");
        valorOleoCampo = campoDialog("Valor pago pelo óleo (R$)");
        valorOleoCampo.setText("55,00");
        f.addView(kmOleoCampo);
        f.addView(valorOleoCampo);
        new AlertDialog.Builder(this)
            .setTitle("Registrar troca de óleo")
            .setView(f)
            .setNegativeButton("Cancelar", (d, w) -> tocarSom())
             .setPositiveButton("Salvar", (d, n) -> {
                tocarSom();
                trocarOleo();
            })
            .show();
    }

    void menuRelatorios() {
        new AlertDialog.Builder(this)
            .setTitle("📋 RELATÓRIOS")
            .setItems(new String[]{"Gerar e compartilhar PDF", "Ver histórico"}, (d, n) -> { tocarSom(); tocarSom();
                if (n == 0) gerarPdf();
                else escolherRegistro();
            }).show();
    }

    void menuDados() {
        new AlertDialog.Builder(this)
            .setTitle("⚙️ DADOS")
            .setItems(new String[]{"Resetar todos os dados", "Verificar atualização", "Cancelar"}, (d, n) -> { tocarSom();
                if (n == 0) confirmarReset();
                else if (n == 1) verificarAtualizacao();
            }).show();
    }


    boolean versaoMaior(String nova, String atual) {
        try {
            String[] a = nova.replace("v","").split("\\.");
            String[] b = atual.replace("v","").split("\\.");
            for (int i=0; i<Math.max(a.length,b.length); i++) {
                int x = i<a.length ? Integer.parseInt(a[i]) : 0;
                int y = i<b.length ? Integer.parseInt(b[i]) : 0;
                if (x != y) return x > y;
            }
        } catch (Exception e) { return !nova.equals(atual); }
        return false;
    }

    void verificarAtualizacao() {
        verificarAtualizacao(false);
    }

    void verificarAtualizacao(boolean automatico) {
        if (!automatico) mensagem("Verificando atualizações...");
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(
                    "https://api.github.com/repos/testeapp202627-star/MotoGastos-Atualizacoes/releases/latest");
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)url.openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(c.getInputStream()));
                StringBuilder json = new StringBuilder();
                String linha;
                while ((linha=r.readLine())!=null) json.append(linha);
                r.close();
                c.disconnect();

                org.json.JSONObject release = new org.json.JSONObject(json.toString());
                String tag = release.getString("tag_name");
                String notas = release.optString("body", "Nova versão disponível.");
                org.json.JSONArray assets = release.getJSONArray("assets");
                String apk = "";
                for (int i=0; i<assets.length(); i++) {
                    org.json.JSONObject item = assets.getJSONObject(i);
                    if (item.getString("name").toLowerCase().endsWith(".apk")) {
                        apk = item.getString("browser_download_url");
                        break;
                    }
                }
                String atual = getPackageManager().getPackageInfo(getPackageName(),0).versionName;
                final String versaoNova = tag;
                final String endereco = apk;
                final String descricao = notas;
                runOnUiThread(() -> {
                    if (!versaoMaior(versaoNova, atual)) {
                        mensagem("Seu MotoGastos já está atualizado.");
                    } else if (endereco.isEmpty()) {
                        mensagem("A versão foi encontrada, mas não há APK anexado no GitHub.");
                    } else {
                        new AlertDialog.Builder(this)
                            .setTitle("Nova versão disponível: " + versaoNova)
                            .setMessage(descricao + "\n\nVersão instalada: " + atual)
                            .setPositiveButton("Baixar e instalar", (d,w) -> baixarAtualizacao(endereco))
                            .setNegativeButton("Depois", null)
                            .show();
                    }
                });
            } catch (Exception e) {
                if (!automatico) runOnUiThread(() -> mensagem("Não foi possível verificar. Confira sua internet."));
            }
        }).start();
    }

    void baixarAtualizacao(String endereco) {
        try {
            DownloadManager dm = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(endereco));
            req.setTitle("Atualização MotoGastos");
            req.setDescription("Baixando nova versão...");
            req.setMimeType("application/vnd.android.package-archive");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "MotoGastos-atualizacao.apk");
            downloadAtualizacao = dm.enqueue(req);
            mensagem("Download iniciado. Aguarde a conclusão.");
        } catch (Exception e) {
            mensagem("Erro ao iniciar download: " + e.getMessage());
        }
    }

    void instalarAtualizacao(long id) {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                prefs.edit().putLong("downloadPendente", id).apply();
                Intent config = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
                startActivity(config);
                mensagem("Autorize a instalação de aplicativos para continuar.");
                return;
            }
            DownloadManager dm = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            android.database.Cursor cursor = dm.query(new DownloadManager.Query().setFilterById(id));
            if (cursor == null || !cursor.moveToFirst()) {
                if (cursor != null) cursor.close();
                mensagem("Não foi possível localizar o APK baixado.");
                return;
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            cursor.close();
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                mensagem("O download não foi concluído corretamente.");
                return;
            }
            Uri arquivo = dm.getUriForDownloadedFile(id);
            if (arquivo == null) {
                mensagem("Não foi possível abrir o APK baixado.");
                return;
            }
            Intent instalar = new Intent(Intent.ACTION_VIEW);
            instalar.setDataAndType(arquivo, "application/vnd.android.package-archive");
            instalar.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(instalar);
        } catch (Exception e) {
            mensagem("Erro ao abrir instalador: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        long pendente = prefs.getLong("downloadPendente", -1);
        if (pendente != -1 && (Build.VERSION.SDK_INT < 26 ||
            getPackageManager().canRequestPackageInstalls())) {
            prefs.edit().remove("downloadPendente").apply();
            instalarAtualizacao(pendente);
        }
    }


    LinearLayout cartao(String titulo, String valor, int cor) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(1);
        c.setPadding(16,18,16,18);

        android.graphics.drawable.GradientDrawable fundo =
            new android.graphics.drawable.GradientDrawable();
        fundo.setColor(Color.WHITE);
        fundo.setCornerRadius(22);
        fundo.setStroke(2, Color.rgb(225,235,227));
        c.setBackground(fundo);
        c.setElevation(5);

        TextView t = new TextView(this);
        t.setText(titulo);
        t.setTextSize(14);
        t.setTextColor(Color.DKGRAY);

        TextView v = new TextView(this);
        v.setText(valor);
        v.setTextSize(21);
        v.setTextColor(cor);
        v.setTypeface(null,Typeface.BOLD);
        v.setPadding(0,10,0,0);

        c.addView(t);
        c.addView(v);
        return c;
    }

    void linhaCartoes(String t1, String v1, String t2, String v2) {
        LinearLayout linha = new LinearLayout(this);
        linha.setOrientation(0);

        LinearLayout.LayoutParams item =
            new LinearLayout.LayoutParams(0,-2,1);
        item.setMargins(5,5,5,12);

        linha.addView(cartao(t1,v1,VERDE),item);
        linha.addView(cartao(t2,v2,VERDE),item);
        painel.addView(linha);
    }

    void atualizar() {
        double km = prefs.getFloat("km",0);
        double inicial = prefs.getFloat("kmInicial",0);
        double kmOleo = prefs.getFloat("kmOleo",(float)inicial);
        double faltam = kmOleo + INTERVALO_OLEO - km;
        double proximaTroca = kmOleo + INTERVALO_OLEO;

        String situacao = faltam <= 0
            ? "🔴 TROCA ATRASADA"
            : faltam <= 100
                ? "🟠 ATENÇÃO: TROCA PRÓXIMA"
                : "🟢 ÓLEO EM DIA";

        String aviso = faltam <= 0
            ? "Atrasada há " + String.format(Locale.getDefault(),"%.0f km",Math.abs(faltam))
            : "Faltam " + String.format(Locale.getDefault(),"%.0f km",faltam) + " para trocar";

        painel.removeAllViews();

        TextView tituloPainel = new TextView(this);
        tituloPainel.setText("RESUMO DA SUA MOTO");
        tituloPainel.setTextSize(19);
        tituloPainel.setTextColor(VERDE);
        tituloPainel.setTypeface(null,Typeface.BOLD);
        tituloPainel.setPadding(8,15,8,12);
        painel.addView(tituloPainel);

        linhaCartoes("🏍️ QUILOMETRAGEM",
            String.format(Locale.getDefault(),"%.0f km",km),
            "⛽ ABASTECIMENTOS",
            String.valueOf(prefs.getInt("quantidade",0)));

        linhaCartoes("💰 COMBUSTÍVEL",
            dinheiro(prefs.getFloat("totalCombustivel",0)),
            "🔧 GASTOS COM ÓLEO",
            dinheiro(prefs.getFloat("totalOleo",0)));

        LinearLayout total = cartao(
            "💰 TOTAL GASTO",
            dinheiro(prefs.getFloat("totalCombustivel",0)
                + prefs.getFloat("totalOleo",0)),
            VERDE
        );
        LinearLayout.LayoutParams margemTotal =
            new LinearLayout.LayoutParams(-1,-2);
        margemTotal.setMargins(5,5,5,12);
        painel.addView(total,margemTotal);

        LinearLayout oleo = cartao(
            "🔧 CONTROLE DE TROCA DE ÓLEO",
            situacao + "\n" + aviso +
            "\nÚltima troca: " + String.format(Locale.getDefault(),"%.0f km",kmOleo) +
            "\nPróxima troca: " + String.format(Locale.getDefault(),"%.0f km",proximaTroca),
            faltam <= 0 ? Color.RED :
            faltam <= 100 ? Color.rgb(220,130,0) : VERDE
        );

        LinearLayout.LayoutParams margem =
            new LinearLayout.LayoutParams(-1,-2);
        margem.setMargins(5,5,5,12);
        painel.addView(oleo,margem);

        historicoView.setText(
            prefs.getString("historico","Nenhum registro ainda."));
        verificarAvisoOleo();
    }

    void abastecer() {
        try {
            double km = numero(kmCampo);
            double litros = numero(litrosCampo);
            double preco = numero(precoCampo);

            if (km <= 0 || litros <= 0 || preco <= 0) {
                mensagem("Preencha KM, litros e preço corretamente.");
                return;
            }

            double anterior = prefs.getFloat("km", (float) km);

            if (km < anterior) {
                mensagem("O KM não pode ser menor que o último registrado.");
                return;
            }

            double rodado = km - anterior;
            double total = litros * preco;
            double consumo = rodado > 0 ? rodado / litros : 0;

            String registro = "ABASTECIMENTO — " + agora()
                + "\nKM: " + km
                + "\nKM percorridos: " + rodado
                + "\nLitros: " + litros
                + "\nPreço por litro: " + dinheiro(preco)
                + "\nValor pago: " + dinheiro(total)
                + (rodado > 0
                    ? "\nConsumo estimado: " +
                        String.format(Locale.getDefault(),
                            "%.2f km/L", consumo) : "")
                + "\n\n";

            prefs.edit()
                .putFloat("km", (float) km)
                .putFloat("totalCombustivel",
                    prefs.getFloat("totalCombustivel",0)
                        + (float) total)
                .putInt("quantidade",
                    prefs.getInt("quantidade",0) + 1)
                .putString("historico",
                    registro + prefs.getString("historico",""))
                .apply();

            atualizar(); verificarAtualizacao(true);
            litrosCampo.setText("");
            kmCampo.setText("");
            mensagem("Abastecimento salvo!\nTotal: " + dinheiro(total));

        } catch (Exception e) {
            mensagem("Confira os valores digitados.");
        }
    }

    void trocarOleo() {
        try {
            double km = numero(kmOleoCampo);
            double valor = numero(valorOleoCampo);

            if (km <= 0 || valor < 0) {
                mensagem("Informe o KM e o valor do óleo.");
                return;
            }

            double kmAtual = prefs.getFloat("km", (float) km);

            if (km < kmAtual) {
                mensagem("O KM da troca não pode ser menor que o KM atual.");
                return;
            }

            String registro = "TROCA DE ÓLEO — " + agora()
                + "\nKM: " + km
                + "\nValor: " + dinheiro(valor)
                + "\nPróxima troca: " + (km + INTERVALO_OLEO)
                + " km\n\n";

            prefs.edit()
                .putFloat("km", (float) km)
                .putFloat("kmOleo", (float) km)
                .putBoolean("avisoOleo100", false)
                .putBoolean("avisoOleo50", false)
                .putBoolean("avisoOleoVencido", false)
                .putFloat("totalOleo",
                    prefs.getFloat("totalOleo",0) + (float) valor)
                .putString("historico",
                    registro + prefs.getString("historico",""))
                .apply();

            atualizar(); verificarAtualizacao(true);
            kmOleoCampo.setText("");
            mensagem("Troca de óleo registrada!");

        } catch (Exception e) {
            mensagem("Confira os valores da troca de óleo.");
        }
    }


    void escolherRegistro() {
        String historico = prefs.getString("historico", "");
        String[] registros = historico.split("\\n\\s*\\n");
        java.util.ArrayList<String> lista = new java.util.ArrayList<>();
        for (String r : registros) if (!r.trim().isEmpty()) lista.add(r.trim());
        if (lista.isEmpty()) { mensagem("Nenhum registro para editar."); return; }
        String[] titulos = new String[lista.size()];
        for (int i = 0; i < lista.size(); i++) titulos[i] = lista.get(i).split("\\n")[0];
        new AlertDialog.Builder(this).setTitle("Escolha um registro")
             .setItems(titulos, (dialog, qual) -> {
                tocarSom();
                menuRegistro(lista, qual);
            })
            .setNegativeButton("Cancelar", (d, w) -> tocarSom()).show();
    }
    void menuRegistro(java.util.ArrayList<String> lista, int pos) {
        String[] opcoes = {"Editar registro", "Excluir registro"};
        new AlertDialog.Builder(this).setTitle(lista.get(pos).split("\\n")[0])
            .setItems(opcoes, (d, escolha) -> { tocarSom();
                if (escolha == 0) editarRegistro(lista, pos);
                else confirmarExclusao(lista, pos);
            }).setNegativeButton("Voltar", (d, w) -> tocarSom()).show();
    }
    void editarRegistro(java.util.ArrayList<String> lista, int pos) {
        EditText campo = new EditText(this);
        campo.setText(lista.get(pos));
        campo.setGravity(android.view.Gravity.TOP);
        campo.setMinLines(6);
        campo.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        new AlertDialog.Builder(this).setTitle("Edite os dados do registro")
            .setMessage("Altere as linhas necessárias sem apagar os nomes dos campos.")
            .setView(campo).setNegativeButton("Cancelar", (d, w) -> tocarSom())
            .setPositiveButton("Salvar", (d, w) -> { tocarSom();
                String novo = campo.getText().toString().trim();
                if (novo.isEmpty() || !(novo.startsWith("ABASTECIMENTO") || novo.startsWith("TROCA DE ÓLEO"))) {
                    mensagem("Registro inválido. Mantenha o tipo no início."); return;
                }
                lista.set(pos, novo); salvarListaHistorico(lista);
                mensagem("Registro atualizado. Confira os totais no painel.");
            }).show();
    }
    void confirmarExclusao(java.util.ArrayList<String> lista, int pos) {
        new AlertDialog.Builder(this).setTitle("Excluir registro?")
            .setMessage("Esta ação não pode ser desfeita.")
            .setNegativeButton("Cancelar", (d, w) -> tocarSom())
            .setPositiveButton("Excluir", (d, w) -> { tocarSom();
                lista.remove(pos); salvarListaHistorico(lista);
                mensagem("Registro excluído do histórico.");
            }).show();
    }
    double valorRegistro(String registro, String campo, boolean moeda) {
        for (String linha : registro.split("\\n")) {
            String prefixo = campo + ":";
            if (linha.trim().startsWith(prefixo)) {
                String valor = linha.trim().substring(prefixo.length()).trim();
                if (moeda) {
                    valor = valor.replace("R$", "").replace(" ", "").replace(".", "").replace(",", ".");
                } else {
                    valor = valor.replace(",", ".");
                }
                try { return Double.parseDouble(valor); }
                catch (Exception e) { return 0; }
            }
        }
        return 0;
    }

    void salvarListaHistorico(java.util.ArrayList<String> lista) {
        StringBuilder sb = new StringBuilder();
        int quantidade = 0;
        double totalCombustivel = 0;
        double totalOleo = 0;
        double maiorKm = prefs.getFloat("kmInicial", 0);
        double maiorKmOleo = prefs.getFloat("kmInicial", 0);

        for (String r : lista) {
            String registro = r.trim();
            if (registro.isEmpty()) continue;
            sb.append(registro).append("\n\n");
            double kmRegistro = valorRegistro(registro, "KM", false);
            if (kmRegistro > maiorKm) maiorKm = kmRegistro;

            if (registro.startsWith("ABASTECIMENTO")) {
                quantidade++;
                totalCombustivel += valorRegistro(registro, "Valor pago", true);
            } else if (registro.startsWith("TROCA DE ÓLEO")) {
                totalOleo += valorRegistro(registro, "Valor", true);
                if (kmRegistro > maiorKmOleo) maiorKmOleo = kmRegistro;
            }
        }

        prefs.edit()
            .putString("historico", sb.toString())
            .putInt("quantidade", quantidade)
            .putFloat("totalCombustivel", (float) totalCombustivel)
            .putFloat("totalOleo", (float) totalOleo)
            .putFloat("km", (float) maiorKm)
            .putFloat("kmOleo", (float) maiorKmOleo)
            .apply();
        atualizar(); verificarAtualizacao(true);
    }

    void confirmarReset() {
        new AlertDialog.Builder(this)
            .setTitle("Apagar todos os dados?")
            .setMessage("Todos os abastecimentos, gastos, quilometragem e histórico serão apagados. Essa ação não pode ser desfeita.")
            .setNegativeButton("CANCELAR", (d, w) -> tocarSom())
            .setPositiveButton("SIM, APAGAR", (d, w) -> { tocarSom();
                prefs.edit().clear().apply();
                kmCampo.setText("");
                litrosCampo.setText("");
                precoCampo.setText("6,80");
                kmOleoCampo.setText("");
                valorOleoCampo.setText("55,00");
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancelAll();
                atualizar(); verificarAtualizacao(true);
                mensagem("Todos os dados foram apagados!");
            }).show();
    }

    void mensagem(String texto) {
        new AlertDialog.Builder(this)
            .setMessage(texto)
            .setPositiveButton("OK", null)
            .show();
    }

    void gerarPdf() {
        android.graphics.pdf.PdfDocument pdf =
            new android.graphics.pdf.PdfDocument();

        try {
            android.graphics.Paint tinta = new android.graphics.Paint();
            tinta.setColor(Color.BLACK);
            tinta.setTextSize(12);

            android.graphics.pdf.PdfDocument.PageInfo info =
                new android.graphics.pdf.PdfDocument.PageInfo.Builder(
                    595, 842, 1).create();

            android.graphics.pdf.PdfDocument.Page pagina =
                pdf.startPage(info);

            Canvas c = pagina.getCanvas();
            int y = 45;

            tinta.setTextSize(22);
            tinta.setTypeface(Typeface.DEFAULT_BOLD);
            c.drawText("RELATORIO MOTOGASTOS", 35, y, tinta);

            tinta.setTextSize(12);
            tinta.setTypeface(Typeface.DEFAULT);
            y += 30;

            c.drawText("Gerado em: " + agora(), 35, y, tinta);
            y += 28;

            String historico = prefs.getString(
                "historico", "Nenhum registro.");

            String[] linhas = (
                "KM atual: " + prefs.getFloat("km", 0)
                + "\nTotal combustível: " +
                    dinheiro(prefs.getFloat("totalCombustivel", 0))
                + "\nTotal óleo: " +
                    dinheiro(prefs.getFloat("totalOleo", 0))
                + "\n\nHISTORICO\n\n" + historico
            ).split("\n");

            for (String linha : linhas) {
                if (y > 800) {
                    pdf.finishPage(pagina);
                    info = new android.graphics.pdf.PdfDocument.PageInfo
                        .Builder(595, 842, pdf.getPages().size() + 1)
                        .create();
                    pagina = pdf.startPage(info);
                    c = pagina.getCanvas();
                    y = 40;
                }

                String texto = linha.length() > 85
                    ? linha.substring(0, 85) : linha;

                c.drawText(texto, 35, y, tinta);
                y += 17;
            }

            pdf.finishPage(pagina);

            if (android.os.Build.VERSION.SDK_INT < 29) {
                mensagem("Para gerar o PDF neste Android, precisamos ajustar a forma de salvar o arquivo.");
                return;
            }

            android.content.ContentValues valores =
                new android.content.ContentValues();

            valores.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                "Relatorio_MotoGastos.pdf");

            valores.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,
                "application/pdf");

            valores.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                android.os.Environment.DIRECTORY_DOWNLOADS);

            android.net.Uri uri = getContentResolver().insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                valores);

            if (uri == null) {
                mensagem("Não foi possível criar o arquivo PDF.");
                return;
            }

            java.io.OutputStream saida =
                getContentResolver().openOutputStream(uri);

            if (saida == null) {
                mensagem("Não foi possível salvar o PDF.");
                return;
            }

            pdf.writeTo(saida);
            saida.close();

            Intent compartilhar = new Intent(Intent.ACTION_SEND);
            compartilhar.setType("application/pdf");
            compartilhar.putExtra(Intent.EXTRA_STREAM, uri);
            compartilhar.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(
                compartilhar, "Compartilhar relatório MotoGastos"));

        } catch (Exception e) {
            mensagem("Erro ao gerar PDF: " + e.getMessage());
        } finally {
            pdf.close();
        }
    }
}
