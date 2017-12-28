package com.example.administrator.Matome_Client;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    Toolbar toolbar;
    TextView show;
    Button btnClear, btnUpd;
    Handler handler;
    String ip;
    int myPort;
    // サーバと通信するスレッド
    ClientThread clientThread;
    NfcTags nfcTags = null;
    //インスタンス化無しで使える
    ProcessCommand pc;
    private static final int SETTING = 8888;
    //バイブ
    Vibrator vib;
    private long m_vibPattern_read[] = {0, 200};
    private long m_vibPattern_error[] = {0, 200, 200, 500};
    private String mVkon;
    //リスト用のデータを準備
    List<Data> mDataList = new ArrayList<>();
    private int mDisplayMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //ハンドラー
        handler = new Handler()
        {
            @Override
            public void handleMessage(Message msg) {
                // サブスレッドからのメッセージ
                if (msg.what == 0x123) {
                    // 表示する
                    String sMsg = msg.obj.toString();
                    //show.append("\n PCから受信-" + sMsg);
                    selectMotionWhenReceiving(sMsg);
                }
            }
        };
        //接続先を取得
        SharedPreferences prefs = getSharedPreferences("ConnectionData", Context.MODE_PRIVATE);
        ip = prefs.getString("ip", "");
        myPort = prefs.getInt("myPort", 0);
        clientThread = new ClientThread(handler, ip, myPort, true);
        // サーバ接続スレッド開始
        new Thread(clientThread).start();

        //バイブ
        vib = (Vibrator)getSystemService(VIBRATOR_SERVICE);
        // view取得
        toolbar = (Toolbar) findViewById(R.id.toolBar);
        setViews();
        //NFCタグ
        this.nfcTags = new NfcTags(this);
    }

    private void changeMode(int mode, String info) {
        String title;
        int layoutId;

        mDisplayMode = mode;
        //
        switch (mode) {
            case 1:
                title = "排出";
                layoutId = R.layout.discharge;
                show.setText("No.1の缶タグをタッチしてください。");
                break;
            case 2:
                title = "比重";
                layoutId = R.layout.hijuu;
                show.setText("缶タグをタッチしてください。");
                break;
            default:
                return;
        }
        //工程名表示
        toolbar.setTitle(title);
        // 変更したいレイアウトを取得する
        LinearLayout layout = (LinearLayout)findViewById(R.id.layout);
        // レイアウトのビューをすべて削除する
        layout.removeAllViews();
        // レイアウトを変更する
        getLayoutInflater().inflate(layoutId, layout);
        //変更後のレイアウトをいじるための準備
        View view = getLayoutInflater().inflate(layoutId, layout);

        //モード毎の本処理
        switch (mode) {
            case 1:
                transDischargeMode(mode, info, view);
                break;
            default:
                return;
        }
    }

    //List表示汎用にするか？
    private void transDischargeMode(int mode, String info, View view) {
        int listId;
        String[] infoset;
        String[] items;
        TextView txt1;
        TextView txt2;

        switch (mode) {
            case 1:
                listId = R.id.listCan;
                infoset = info.split("@");
                txt1 = (TextView) view.findViewById(R.id.txtVko);
                txt2 = (TextView) view.findViewById(R.id.txtCankei);
                break;
            default:
                return;
        }

        ListView listView = (ListView) view.findViewById(listId);

        //データ格納
        int cankei = 0;
        for (int i = 0; i < infoset.length; i++) {
            if (infoset[i].equals("")) {
                continue;
            }

            Data data = new Data();
            items = infoset[i].split(",");
            for (int j = 0; j < items.length; j++) {
                switch (j) {
                    case 0: data.setNumber(items[j]);
                        break;
                    case 1: data.setZainmk(items[j]);
                        break;
                    case 2: data.setKokban(items[j]);
                        break;
                    case 3: data.setCansuu(items[j]);
                        break;
                    case 4: data.setVkonno(items[j]);
                        break;
                    case 5: data.setMeiban(items[j]);
                        break;
                    default:
                }
            }
            mDataList.add(data);
            cankei++;
        }

        //画面に出力
        txt1.setText(mVkon);
        txt2.setText(String.valueOf(cankei));
        // リストにデータを受け渡す
        ListAdapter adapter = new ListAdapter(this, mDataList);
        listView.setAdapter(adapter);
    }

    private void cantagScannedCheck(String can) {
        for (Data data : mDataList) {
            //缶タグが入ってる場合はスキップ
            if (!data.getCanTag().equals("")) {
                //缶タグがスキャン済みの場合はアラート
                if (data.getCanTag().equals(can)) {
                    show.setText("缶タグ(" + can + ")はスキャン済みです。");
                    //バイブ エラー
                    vib.vibrate(m_vibPattern_error, -1);
                    return;
                }
                continue;
            }
            data.setCanTag(can);
            break;
        }
        // リストにデータを受け渡す
        ListView listView = (ListView) findViewById(R.id.listCan);
        ListAdapter adapter = new ListAdapter(this, mDataList);
        listView.setAdapter(adapter);

        //登録可能かチェックして、登録ボタンを有効化する
        confirmRegisterable();
    }

    private void confirmRegisterable() {
        int number = 0;
        for (Data data : mDataList) {
            number++;
            if (data.getCanTag().equals("")) {
                show.setText("No." + String.valueOf(number) + "の缶タグをタッチしてください。");
                return;
            }
        }
        //登録ボタンを無効化
        btnUpd.setEnabled(false);
        setShowMessage(99);
    }

    //受信した文字列のコマンド値によって分岐（switch文ではenum使えず...if文汚し）
    private void selectMotionWhenReceiving(String sMsg) {
        String cmd = pc.COMMAND_LENGTH.getCmdText(sMsg);
        String excmd  = pc.COMMAND_LENGTH.getExcludeCmdText(sMsg);

        if (cmd.equals(pc.SAG.getString())) {
            //作業者名をセット
            if (!excmd.equals("")) {
                setShowMessage(0);
            }
        }
        else if (cmd.equals(pc.VKO.getString())) {
            changeMode(1, excmd);
        }
        else if (cmd.equals(pc.DUP.getString())
                || cmd.equals(pc.HUP.getString())
                ) {
            MyToast.makeText(this, "登録完了しました。", Toast.LENGTH_SHORT, 32f).show();
            initPage();
        }
        else if (cmd.equals(pc.MSG.getString())) {
            show.setText(excmd);
        }
        else if (cmd.equals(pc.ERR.getString())) {
            //バイブ エラー
            vib.vibrate(m_vibPattern_error, -1);
        }
    }

    //タグテキストのコマンド値によって分岐
    private void selectMotionTagText(String sMsg) {
        String cmd = pc.COMMAND_LENGTH.getCmdText(sMsg);
        String excmd  = pc.COMMAND_LENGTH.getExcludeCmdText(sMsg);

        if (cmd.equals(pc.VKO.getString())) {
            if (mDisplayMode == 0) {
                //TAGテキストをそのまま送信
                sendMsgToServer(sMsg);
                mVkon = excmd;
            }
        }
        else if (cmd.equals(pc.CAN.getString())) {
            if (mDisplayMode != 0) {
                if (mDisplayMode == 2) {
                    sendMsgToServer(pc.HUP.getString() + sMsg);
                }
                else {
                    cantagScannedCheck(sMsg);
                }
            }
        }
        else if (cmd.equals(pc.OPE.getString())) {
            if (mDisplayMode == 0) {
                switch (excmd) {
                    case "HIJUU":
                        changeMode(2, "");
                        break;
                    case "KENRYO":
                        changeMode(3, "");
                        break;
                    case "HOKAN":
                        changeMode(4, "");
                        break;
                }
            }
        }
        else if (cmd.equals(pc.KOB.getString())) {
            //TAGテキストをそのまま送信
            sendMsgToServer(sMsg);
        }
        else {
            show.setText("タグテキストエラー！");
        }
    }

    private void initPage() {
        //登録ボタンを無効化
        btnUpd.setEnabled(false);
        setShowMessage(0);

        //リスト用データを初期化
        mDataList = new ArrayList<>();
        //TOPに戻る
        mDisplayMode = 0;
        toolbar.setTitle("まとめ配合TOP");
        LinearLayout layout = (LinearLayout)findViewById(R.id.layout);
        layout.removeAllViews();
        getLayoutInflater().inflate(R.layout.nfcimag, layout);
    }

    @Override
    //クリック処理の実装
    public void onClick(View v) {
        if (v != null) {
            switch (v.getId()) {
                case R.id.btnUpd :
                    //Dialog(OK,Cancel Ver.)
                    new AlertDialog.Builder(this)
                            .setTitle("確認")
                            .setMessage("登録しますか？")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // OK button pressed
                                    switch (mDisplayMode) {
                                        case 1:
                                            sendMsgToServer(pc.DUP.getString() + createUpdtext_D());
                                            break;
                                    }
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    break;

                case R.id.btnClear :
                    //Dialog(OK,Cancel Ver.)
                    new AlertDialog.Builder(this)
                            .setTitle("確認")
                            .setMessage("クリアしてよろしいですか？")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // OK button pressed
                                    initPage();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    break;

            }
        }
    }

    //登録ボタン押下時にサーバに送る更新値の生成
    private String createUpdtext_D() {
        String txt = "";

        for (Data data : mDataList) {
            txt += "@";
            txt += data.getCanTag() + ",";
            txt += data.getVkonno() + ",";
            txt += data.getMeiban();
        }
        return txt;
    }

    @Override
    //タグを読み込んだ時に実行される
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String tagText = "";
        tagText = this.nfcTags.getTagText(intent);
        if (!tagText.equals("")) {
            selectMotionTagText(tagText);
        }
        //バイブ
        vib.vibrate(m_vibPattern_read, -1);
    }

    //サーバへメッセージを送信する
    private void sendMsgToServer(String sMsg) {
        try {
            // メッセージ送信
            Message msg = new Message();
            msg.what = 0x345;   //？
            msg.obj = sMsg;
            clientThread.revHandler.sendMessage(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setShowMessage(int order) {
        switch (order) {
            case 0:
                show.setText("OPEタグをタッチしてください。");
                break;
            case 1:
                show.setText("機械Noをタッチしてください。");
                break;
            case 2:
                show.setText("タッチしてください。");
                break;
            case 3:
                show.setText("箱をタッチしてください。");
                break;
            case 99:
                show.setText("全てOKです。\n登録してください。");
                btnUpd.setEnabled(true);
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case SETTING:
                Toast.makeText(this, "設定が完了しました。", Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
    }

    private void setViews() {
        toolbar.setTitle("まとめ配合TOP");
        setSupportActionBar(toolbar);

        show = (TextView) findViewById(R.id.show);
        btnClear = (Button) findViewById(R.id.btnClear);
        btnUpd = (Button) findViewById(R.id.btnUpd);
        //クリックイベント
        btnClear.setOnClickListener(this);
        btnUpd.setOnClickListener(this);
        //登録ボタンを無効化
        btnUpd.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PendingIntent pendingIntent = this.createPendingIntent();
        // Enable NFC adapter
        this.nfcTags.enable(this, pendingIntent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Disable NFC adapter
        this.nfcTags.disable(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.nfcTags = null;
        try {
            clientThread.finalize();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK){
            //Dialog(OK,Cancel Ver.)
            new AlertDialog.Builder(this)
                    .setTitle("確認")
                    .setMessage("終了してもよろしいですか？")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // OK button pressed
                            finishAndRemoveTask();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            //設定画面呼び出し
            Intent intent = new Intent(this, Setting.class);
            int requestCode = SETTING;
            startActivityForResult(intent, requestCode);
            return true;
        }
        else if (id == R.id.action_reconnection) {
            show.setText("再接続に失敗しました。\n無線LAN状況を確認してください。");
            //再接続を行う
            clientThread = new ClientThread(handler, ip, myPort, false);
            // サーバ接続スレッド開始
            new Thread(clientThread).start();
        }
        else if (id == R.id.action_finish) {
            //Dialog(OK,Cancel Ver.)
            new AlertDialog.Builder(this)
                    .setTitle("確認")
                    .setMessage("終了してもよろしいですか？")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // OK button pressed
                            finishAndRemoveTask();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
        return super.onOptionsItemSelected(item);
    }


    private PendingIntent createPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(this, 0, intent, 0);
    }
}
