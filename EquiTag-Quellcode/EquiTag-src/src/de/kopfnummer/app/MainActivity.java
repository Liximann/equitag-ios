package de.kopfnummer.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

import static de.kopfnummer.app.I18n.t;

public class MainActivity extends Activity {

    private static final int MENU_SETTINGS = 1;
    private static final int MENU_INFO = 2;
    private static final int PRIMARY = 0xFF1F3A5F;
    private static final int ACCENT = 0xFFC8862A;
    private static final int SUCCESS = 0xFF2E7D32;
    private static final int ERROR = 0xFFC62828;

    private HorseStore store;
    private List<Horse> horses;
    private HorseAdapter adapter;
    private TextView emptyView;
    private final Handler ui = new Handler(Looper.getMainLooper());

    // Zustand der laufenden Übertragung
    private AlertDialog transferDialog;
    private TextView transferStatus;
    private TextView transferInfo;
    private volatile String lastLog = "";
    private ImageView transferPreview;
    private ProgressBar transferProgress;
    private Horse transferHorse;
    private volatile boolean writing;
    private boolean readerActive;

    @Override
    protected void attachBaseContext(Context base) {
        String code = I18n.resolve(new HorseStore(base).getLanguage());
        I18n.setLanguage(code);
        Locale locale = new Locale(code);
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        super.attachBaseContext(base.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new HorseStore(this);
        horses = store.load();
        setTitle(t("title_list"));

        FrameLayout root = new FrameLayout(this);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setClipToPadding(false);
        list.setPadding(dp(12), dp(12), dp(12), dp(96));
        adapter = new HorseAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                startTransfer(horses.get(position));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showHorseActions(horses.get(position));
                return true;
            }
        });
        root.addView(list, new FrameLayout.LayoutParams(-1, -1));

        emptyView = new TextView(this);
        emptyView.setText(t("empty"));
        emptyView.setTextColor(0xFF6B7280);
        emptyView.setTextSize(16);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(dp(32), 0, dp(32), 0);
        root.addView(emptyView, new FrameLayout.LayoutParams(-1, -1));

        TextView fab = new TextView(this);
        fab.setText("+");
        fab.setTextColor(Color.WHITE);
        fab.setTextSize(32);
        fab.setGravity(Gravity.CENTER);
        fab.setIncludeFontPadding(false);
        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setShape(GradientDrawable.OVAL);
        fabBg.setColor(ACCENT);
        fab.setBackground(fabBg);
        fab.setElevation(dp(6));
        fab.setContentDescription(t("add_horse"));
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEditDialog(null);
            }
        });
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.BOTTOM | Gravity.END);
        fabLp.setMargins(0, 0, dp(20), dp(24));
        root.addView(fab, fabLp);

        setContentView(root);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // NFC immer von EquiTag abfangen, solange die App im Vordergrund ist –
        // sonst öffnet z. B. Samsung nach der Übertragung "Neues Tag erkannt".
        enableReader();
    }

    @Override
    protected void onPause() {
        super.onPause();
        disableReader();
    }

    // ----------------------------------------------------------------- Menü

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SETTINGS, 0, t("settings"));
        menu.add(0, MENU_INFO, 1, t("help"));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_SETTINGS) {
            showSettings();
            return true;
        }
        if (item.getItemId() == MENU_INFO) {
            new AlertDialog.Builder(this)
                    .setTitle(t("help_title"))
                    .setMessage(t("help_text"))
                    .setPositiveButton(t("ok"), null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ----------------------------------------------------------------- Einstellungen

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPaddingRelative(dp(24), dp(8), dp(24), dp(8));

        TextView langLabel = label(t("language"));
        box.addView(langLabel);
        TextView langValue = new TextView(this);
        langValue.setText(I18n.NAMES[I18n.index(I18n.resolve(store.getLanguage()))] + "  ▾");
        langValue.setTextSize(18);
        langValue.setTextColor(0xFF111827);
        langValue.setPaddingRelative(dp(4), dp(10), dp(4), dp(14));
        box.addView(langValue);

        final Switch rotSwitch = switchRow(box, t("rotate180"), t("rotate_desc"), store.isRotate180());
        final Switch invSwitch = switchRow(box, t("invert"), t("invert_desc"), store.isInvert());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(t("settings"))
                .setView(box)
                .setPositiveButton(t("ok"), null)
                .create();
        rotSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                store.setRotate180(checked);
            }
        });
        invSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                store.setInvert(checked);
            }
        });
        langValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickLanguage(dialog);
            }
        });
        dialog.show();
    }

    private Switch switchRow(LinearLayout parent, String title, String desc, boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(dp(4), dp(12), 0, dp(4));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tt = new TextView(this);
        tt.setText(title);
        tt.setTextSize(16);
        tt.setTextColor(0xFF111827);
        TextView td = new TextView(this);
        td.setText(desc);
        td.setTextSize(13);
        td.setTextColor(0xFF6B7280);
        texts.addView(tt);
        texts.addView(td);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        row.addView(sw);
        parent.addView(row);
        return sw;
    }

    private void pickLanguage(final AlertDialog settingsDialog) {
        int current = I18n.index(I18n.resolve(store.getLanguage()));
        new AlertDialog.Builder(this)
                .setTitle(t("language"))
                .setSingleChoiceItems(I18n.NAMES, current, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        store.setLanguage(I18n.CODES[which]);
                        d.dismiss();
                        settingsDialog.dismiss();
                        recreate();
                    }
                })
                .setNegativeButton(t("cancel"), null)
                .show();
    }

    // ----------------------------------------------------------------- Liste

    private void refresh() {
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(horses.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private final class HorseAdapter extends BaseAdapter {
        @Override public int getCount() { return horses.size(); }
        @Override public Object getItem(int position) { return horses.get(position); }
        @Override public long getItemId(int position) { return horses.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Horse h = horses.get(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPaddingRelative(dp(14), dp(14), dp(14), dp(14));
            GradientDrawable card = new GradientDrawable();
            card.setColor(Color.WHITE);
            card.setCornerRadius(dp(14));
            row.setBackground(card);
            row.setElevation(dp(2));

            TextView badge = new TextView(MainActivity.this);
            badge.setText(h.number);
            badge.setTextColor(Color.WHITE);
            badge.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
            badge.setTextSize(22);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(PRIMARY);
            badgeBg.setCornerRadius(dp(10));
            badge.setBackground(badgeBg);
            row.addView(badge, new LinearLayout.LayoutParams(dp(84), dp(56)));

            LinearLayout texts = new LinearLayout(MainActivity.this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPaddingRelative(dp(16), 0, dp(8), 0);
            TextView name = new TextView(MainActivity.this);
            name.setText(h.name);
            name.setTextColor(0xFF111827);
            name.setTextSize(18);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setSingleLine(true);
            TextView hint = new TextView(MainActivity.this);
            hint.setText(t("tap_to_transfer"));
            hint.setTextColor(0xFF6B7280);
            hint.setTextSize(13);
            texts.addView(name);
            texts.addView(hint);
            row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView arrow = new TextView(MainActivity.this);
            arrow.setText(getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? "‹" : "›");
            arrow.setTextSize(28);
            arrow.setTextColor(0xFF9CA3AF);
            row.addView(arrow);

            FrameLayout wrap = new FrameLayout(MainActivity.this);
            wrap.setPadding(0, dp(5), 0, dp(5));
            wrap.addView(row);
            return wrap;
        }
    }

    // ----------------------------------------------------------------- Hinzufügen / Bearbeiten

    private void showEditDialog(final Horse existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPaddingRelative(dp(24), dp(8), dp(24), 0);

        TextView nameLabel = label(t("horse_name"));
        final EditText name = new EditText(this);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(30)});
        name.setHint(t("name_hint"));
        name.setSingleLine(true);

        TextView numLabel = label(t("number_label"));
        final EditText number = new EditText(this);
        number.setInputType(InputType.TYPE_CLASS_NUMBER);
        number.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        number.setHint(t("number_hint"));
        number.setSingleLine(true);

        if (existing != null) {
            name.setText(existing.name);
            number.setText(existing.number);
        }
        form.addView(nameLabel);
        form.addView(name);
        form.addView(numLabel);
        form.addView(number);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? t("add_horse") : t("edit_horse"))
                .setView(form)
                .setPositiveButton(t("save"), null)
                .setNegativeButton(t("cancel"), null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                name.requestFocus();
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String n = name.getText().toString().trim();
                        String k = number.getText().toString().trim();
                        if (n.isEmpty()) { name.setError(t("err_name")); return; }
                        if (!k.matches("\\d{1,4}")) { number.setError(t("err_number")); return; }
                        if (existing == null) {
                            horses.add(new Horse(System.currentTimeMillis(), n, k));
                        } else {
                            int i = indexOf(existing.id);
                            if (i >= 0) horses.set(i, new Horse(existing.id, n, k));
                        }
                        store.save(horses);
                        refresh();
                        dialog.dismiss();
                    }
                });
            }
        });
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    private void showHorseActions(final Horse h) {
        new AlertDialog.Builder(this)
                .setTitle(h.name + " – " + h.number)
                .setItems(new CharSequence[]{t("transfer"), t("edit"), t("delete")}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) startTransfer(h);
                        else if (which == 1) showEditDialog(h);
                        else confirmDelete(h);
                    }
                })
                .show();
    }

    private void confirmDelete(final Horse h) {
        new AlertDialog.Builder(this)
                .setMessage(t("delete_confirm", h.name))
                .setPositiveButton(t("delete"), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int i = indexOf(h.id);
                        if (i >= 0) horses.remove(i);
                        store.save(horses);
                        refresh();
                    }
                })
                .setNegativeButton(t("cancel"), null)
                .show();
    }

    private int indexOf(long id) {
        for (int i = 0; i < horses.size(); i++) if (horses.get(i).id == id) return i;
        return -1;
    }

    // ----------------------------------------------------------------- Übertragung

    private void startTransfer(Horse h) {
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc == null) {
            new AlertDialog.Builder(this)
                    .setTitle(t("no_nfc_title"))
                    .setMessage(t("no_nfc_msg"))
                    .setPositiveButton(t("ok"), null).show();
            return;
        }
        if (!nfc.isEnabled()) {
            new AlertDialog.Builder(this)
                    .setTitle(t("nfc_off_title"))
                    .setMessage(t("nfc_off_msg"))
                    .setPositiveButton(t("settings"), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                        }
                    })
                    .setNegativeButton(t("cancel"), null).show();
            return;
        }

        transferHorse = h;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(16), dp(24), dp(8));
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView preview = new ImageView(this);
        transferPreview = preview;
        Bitmap bmp = LabelRenderer.render(h.number, h.name, LabelRenderer.PREVIEW_WIDTH, LabelRenderer.PREVIEW_HEIGHT);
        BitmapDrawable d = new BitmapDrawable(getResources(), bmp);
        d.setFilterBitmap(false);
        preview.setImageDrawable(d);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(true);
        GradientDrawable pbg = new GradientDrawable();
        pbg.setColor(Color.WHITE);
        pbg.setStroke(dp(1), 0xFFD1D5DB);
        pbg.setCornerRadius(dp(6));
        preview.setBackground(pbg);
        preview.setPadding(dp(4), dp(4), dp(4), dp(4));
        box.addView(preview, new LinearLayout.LayoutParams(-1, -2));

        transferStatus = new TextView(this);
        transferStatus.setText(t("hold_phone"));
        transferStatus.setTextSize(16);
        transferStatus.setTextColor(0xFF111827);
        transferStatus.setGravity(Gravity.CENTER);
        transferStatus.setPadding(0, dp(18), 0, dp(12));
        box.addView(transferStatus, new LinearLayout.LayoutParams(-1, -2));

        transferInfo = new TextView(this);
        transferInfo.setTextSize(12);
        transferInfo.setTextColor(0xFF6B7280);
        transferInfo.setGravity(Gravity.CENTER);
        transferInfo.setVisibility(View.GONE);
        box.addView(transferInfo, new LinearLayout.LayoutParams(-1, -2));

        transferProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        transferProgress.setMax(100);
        transferProgress.setIndeterminate(true);
        box.addView(transferProgress, new LinearLayout.LayoutParams(-1, dp(16)));

        transferDialog = new AlertDialog.Builder(this)
                .setTitle(t("transfer_title", h.name))
                .setView(box)
                .setNegativeButton(t("cancel"), null)
                .setNeutralButton(t("share_log"), null)
                .create();
        transferDialog.setCanceledOnTouchOutside(false);
        transferDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                transferHorse = null;
                transferDialog = null;
            }
        });
        transferDialog.show();
        Button logBtn = transferDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        logBtn.setVisibility(View.GONE);
        logBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, "EquiTag NFC-Protokoll");
                send.putExtra(Intent.EXTRA_TEXT, lastLog);
                startActivity(Intent.createChooser(send, t("share_log")));
            }
        });
        enableReader();
    }

    private void enableReader() {
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc == null || readerActive) return;
        Bundle extras = new Bundle();
        extras.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 5000);
        nfc.enableReaderMode(this, new NfcAdapter.ReaderCallback() {
            @Override
            public void onTagDiscovered(Tag tag) {
                onTag(tag);
            }
        }, NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NFC_F
                | NfcAdapter.FLAG_READER_NFC_V | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
                | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, extras);
        readerActive = true;
    }

    private void disableReader() {
        NfcAdapter nfc = NfcAdapter.getDefaultAdapter(this);
        if (nfc != null && readerActive) {
            try { nfc.disableReaderMode(this); } catch (Exception ignored) {}
        }
        readerActive = false;
    }

    /** Läuft im NFC-Thread. */
    private void onTag(final Tag tag) {
        final Horse h = transferHorse;
        if (h == null || writing || transferDialog == null) return; // außerhalb einer Übertragung: ignorieren
        writing = true;
        vibrate(40);
        final boolean rotate = store.isRotate180();
        final boolean invert = store.isInvert();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    showProgress(1, t("tag_found"), 0xFF111827);
                    EpaperWriter.write(tag, new EpaperWriter.ImageSource() {
                        @Override
                        public boolean[] render(int width, int height) {
                            return LabelRenderer.toDevice(h.number, h.name, width, height, rotate, invert);
                        }
                    }, new EpaperWriter.Progress() {
                        @Override
                        public void onProgress(int percent, String messageKey) {
                            showProgress(percent, t(messageKey), 0xFF111827);
                        }

                        @Override
                        public void onDeviceInfo(int width, int height, int colors) {
                            showDeviceInfo(h, width, height, colors);
                        }
                    });
                    onSuccess();
                } catch (EpaperWriter.WriteException e) {
                    onFailure(e.arg == null ? t(e.getMessage()) : t(e.getMessage(), e.arg));
                } catch (Exception e) {
                    onFailure(t("err_generic", e.getClass().getSimpleName()));
                } finally {
                    writing = false;
                }
            }
        }).start();
    }

    private void showDeviceInfo(final Horse h, final int width, final int height, final int colors) {
        final int lw = Math.max(width, height), lh = Math.min(width, height);
        final Bitmap bmp = LabelRenderer.render(h.number, h.name, lw, lh);
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (transferDialog == null) return;
                BitmapDrawable d = new BitmapDrawable(getResources(), bmp);
                d.setFilterBitmap(false);
                transferPreview.setImageDrawable(d);
                transferInfo.setText(t("display_info", lw, lh, colors));
                transferInfo.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showProgress(final int percent, final String message, final int color) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (transferDialog == null) return;
                transferProgress.setIndeterminate(false);
                transferProgress.setProgress(percent);
                transferStatus.setText(message);
                transferStatus.setTextColor(color);
            }
        });
    }

    private void onSuccess() {
        vibrate(120);
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (transferDialog == null) return;
                transferProgress.setProgress(100);
                transferStatus.setText(t("done"));
                transferStatus.setTextColor(SUCCESS);
                transferStatus.setTextSize(20);
                Button b = transferDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                if (b != null) b.setText(t("close"));
                ui.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (transferDialog != null) transferDialog.dismiss();
                    }
                }, 1800);
            }
        });
    }

    private void onFailure(final String message) {
        lastLog = message + "\n\n" + FmshWriter.log.toString();
        vibrate(400);
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (transferDialog == null) return;
                transferProgress.setIndeterminate(true);
                transferStatus.setText(message + "\n\n" + t("retry_hint"));
                Button lb = transferDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                if (lb != null) lb.setVisibility(View.VISIBLE);
                transferStatus.setTextColor(ERROR);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void vibrate(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(ms);
        } catch (Exception ignored) {}
    }

    // ----------------------------------------------------------------- Helfer

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(0xFF6B7280);
        t.setPaddingRelative(dp(4), dp(12), 0, 0);
        return t;
    }

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
