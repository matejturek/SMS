package x.sms;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;

public class MainActivity extends AppCompatActivity {

    Boolean started;
    TextView textview;
    EditText server_et;
    EditText path_et;
    EditText username_et;
    EditText password_et;
    Button interval_plus, interval_minus;
    Button sim1_btn, sim2_btn;
    int interval = 10;
    int sim = 1;

    Button start_stop_btn;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        started = false;

        /* Inicializacia Buttonov, Editboxov, atd. */

        textview = findViewById(R.id.info1);
        server_et = findViewById(R.id.server);
        path_et = findViewById(R.id.path);
        username_et = findViewById(R.id.username);
        password_et = findViewById(R.id.password);
        interval = Integer.valueOf(((EditText) findViewById(R.id.interval)).getText().toString());
        interval_plus = (Button) findViewById(R.id.interval_plus);
        interval_minus = (Button) findViewById(R.id.interval_minus);
        sim1_btn = (Button) findViewById(R.id.sim1_btn);
        sim2_btn = (Button) findViewById(R.id.sim2_btn);
        start_stop_btn = findViewById(R.id.start_stop);

        loadValues();

        final ArrayList<Integer> simCardList = new ArrayList<>();
        SubscriptionManager subscriptionManager;
        subscriptionManager = SubscriptionManager.from(MainActivity.this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        final List<SubscriptionInfo> subscriptionInfoList = subscriptionManager
                .getActiveSubscriptionInfoList();
        for (SubscriptionInfo subscriptionInfo : subscriptionInfoList) {
            int subscriptionId = subscriptionInfo.getSubscriptionId();
            simCardList.add(subscriptionId);
        }


        /* Zobrazenie hesla */
        CheckBox cb = (CheckBox)this.findViewById(R.id.show_pass);
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean     b) {
                if(!b)
                {
                    password_et.setTransformationMethod(PasswordTransformationMethod.getInstance());
                }
                else
                {
                    password_et.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                }
            }
        });

        /* Inkrementacia intervalu */
        interval_plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                interval++;
                ((EditText) findViewById(R.id.interval)).setText(String.valueOf(interval));
            }
        });
        /* Dekrementacia intervalu */
        interval_minus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (interval > 1) {
                    interval--;
                    ((EditText) findViewById(R.id.interval)).setText(String.valueOf(interval));
                }
            }
        });
        sim1_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sim = 1;
                sim1_btn.setBackgroundResource(R.drawable.my_button);
                sim2_btn.setBackgroundResource(R.drawable.my_button_red);
            }
        });
        sim2_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sim = 2;
                sim1_btn.setBackgroundResource(R.drawable.my_button_red);
                sim2_btn.setBackgroundResource(R.drawable.my_button);
            }
        });


        /* Casovac na pustanie samotneho cyklu appky - ziskava obsah suboru a posiela SMS*/
        final Timer[] timer = new Timer[1];
        start_stop_btn.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onClick(View view) {
                switchstate();
                saveValues();

                if (started) {
                    timer[0] = new Timer();
                    timer[0].schedule(new TimerTask()
                    {
                        @Override
                        public void run()
                        {
                            new Thread(new Runnable() {
                                public void run() {
                                    if (started) {
                                        String server = server_et.getText().toString();
                                        String path = path_et.getText().toString();
                                        String username = username_et.getText().toString();
                                        String password = password_et.getText().toString();

                                        final String line = getFile(server, path, username, password);
                                        textview.post(new Runnable() {
                                            public void run() {
                                                textview.setText(line);
                                                if (line.equals("ERROR: CANNOT READ FILE")) {
                                                    textview.setText("Nelze přečíst soubor nebo soubor neexistuje");
                                                } else {
                                                    textview.setText(line);
                                                    parseSMSLine(line);
                                                }
                                            }
                                        });
                                    } else {
                                        //Do nothing
                                    }
                                }
                            }).start();
                        }
                    }, 0, interval * 60000);
                } else {
                    timer[0].cancel();
                }

            }
        });
    }

    /*
        Ziskanie suboru pomocou SMB protokolu
    */
    String getFile(String ip, String path, String username, String password) {
        SmbFile file;
        String line = null;
        try {
            String url = "smb://" + ip + "/" + path;
            NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(null, username, password);
            file = new SmbFile(url, auth);
            if (file != null) {
                // Nacitaj prvy riadok
                BufferedReader reader = new BufferedReader(new InputStreamReader(new SmbFileInputStream(file)));
                line = reader.readLine();

                //Vytvor novy subor (premenuj stary) na format yyyy-MM-dd-HH-mm"
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
                String currentDateAndTime = sdf.format(new Date());
                String myPath = file.getPath().substring(0, file.getPath().lastIndexOf("/"));
                SmbFile smbToFile = new SmbFile(myPath + "/" + currentDateAndTime + ".txt", auth);
                file.renameTo(smbToFile);
            } else {
                return "ERROR: CANNOT READ FILE";
            }
        } catch (Exception e) {
            Log.d("ERROR", e.toString());
            return "ERROR: CANNOT READ FILE";
        }
        return line;
    }

    /* Zmen stav tlacitiek

    *  Appka bezi - tlacitka zakaz
    *  Appka je zastavena - tlacitka su povolene
    *
    *  */
    void switchstate() {
        if (started) {
            start_stop_btn.setText("Start");
        } else {
            start_stop_btn.setText("Stop");
        }
        server_et.setEnabled(started);
        path_et.setEnabled(started);
        username_et.setEnabled(started);
        password_et.setEnabled(started);
        findViewById(R.id.interval).setEnabled(started);

        started = !started;
    }

    /*
        Ulozenie obsahu policok, aby si to appka pamatala pri dalsom spusteni
     */
    void saveValues() {
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("server", server_et.getText().toString());
        editor.putString("path", path_et.getText().toString());
        editor.putString("user", username_et.getText().toString());
        editor.putString("pass", password_et.getText().toString());
        editor.putInt("interval", interval);
        editor.putInt("sim", sim);
        editor.commit();
    }

    /*
        Nacitaj obsah policok
     */
    void loadValues() {
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        server_et.setText(sharedPref.getString("server", ""));
        path_et.setText(sharedPref.getString("path", ""));
        username_et.setText(sharedPref.getString("user", ""));
        password_et.setText(sharedPref.getString("pass", ""));
        ((EditText)findViewById(R.id.interval)).setText(String.valueOf(sharedPref.getInt("interval", 10)));
        interval = sharedPref.getInt("interval", 10);
        if (sim == 1) {
            sim1_btn.setBackgroundResource(R.drawable.my_button);
            sim2_btn.setBackgroundResource(R.drawable.my_button_red);
        } else {
            sim1_btn.setBackgroundResource(R.drawable.my_button_red);
            sim2_btn.setBackgroundResource(R.drawable.my_button);
        }
    }

    /*
        Vytvor SMSky a posli ich
     */
    void parseSMSLine(String text) {
        String[] stringArray = text.split(";");

        for(int i = 0; i < stringArray.length; i+=2) {
            SMS sms = new SMS(stringArray[i], stringArray[i+1], sim);
            sms.sendSMS();
        }
    }
}