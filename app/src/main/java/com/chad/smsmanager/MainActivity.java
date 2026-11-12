package com.chad.smsmanager;

import android.Manifest;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.provider.Telephony;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    final ArrayList<Sms> rows = new ArrayList<>();
    final HashSet<Long> selected = new HashSet<>();
    EditText keyword, sender; TextView status, recovery, operation; BaseAdapter adapter; Button delete;
    final Handler ui = new Handler(Looper.getMainLooper());
    boolean observedRunning;
    final Runnable monitor = new Runnable() { public void run() {
        boolean now = CleanupTask.isRunning();
        refreshSafety();
        if (observedRunning && !now) load();
        observedRunning = now;
        ui.postDelayed(this, 1000);
    }};
    boolean busy; int generation; ArrayList<Sms> exportSnapshot;
    static class Sms {
        long id, date; String address, body; int type;
        Sms(Cursor c) { id=c.getLong(0); address=c.getString(1); body=c.getString(2); date=c.getLong(3); type=c.getInt(4); }
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,16,24,16);
        root.setOnApplyWindowInsetsListener((v,insets)->{ android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()); v.setPadding(24,i.top+16,24,i.bottom+16); return insets; });
        TextView title=new TextView(this); title.setText("短信清理"); title.setTextSize(26); root.addView(title);
        TextView hint=new TextView(this); hint.setText("本机离线处理 · 不上传短信\n仅普通 SMS，不含彩信、RCS 或联系人姓名搜索"); root.addView(hint);
        keyword=new EditText(this); keyword.setHint("正文关键词（按字面匹配）"); keyword.setSingleLine(true); root.addView(keyword);
        sender=new EditText(this); sender.setHint("发件号码 / 发件人标识（包含匹配）"); sender.setSingleLine(true); root.addView(sender);
        LinearLayout search=new LinearLayout(this); root.addView(search);
        addButton(search,"查询",v->load()); addButton(search,"清空条件",v->{ keyword.setText(""); sender.setText(""); load(); });
        recovery=new TextView(this); recovery.setTextSize(13); root.addView(recovery);
        operation=new TextView(this); operation.setTextSize(13); root.addView(operation);
        status=new TextView(this); root.addView(status);
        LinearLayout actions=new LinearLayout(this); root.addView(actions);
        addButton(actions,"全选结果",v->{ if(busy||CleanupTask.isRunning())return; for(Sms s:rows)selected.add(s.id); update(); });
        addButton(actions,"取消选择",v->{ if(busy||CleanupTask.isRunning())return; selected.clear(); update(); });
        addButton(actions,"备份所选",v->export());
        ListView list=new ListView(this); root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        adapter=new BaseAdapter(){
            public int getCount(){return rows.size();} public Object getItem(int i){return rows.get(i);} public long getItemId(int i){return rows.get(i).id;}
            public View getView(int i,View old,ViewGroup parent){
                Sms s=rows.get(i); LinearLayout box=new LinearLayout(MainActivity.this); box.setPadding(0,12,0,12);
                CheckBox check=new CheckBox(MainActivity.this); check.setChecked(selected.contains(s.id)); check.setEnabled(!busy&&!CleanupTask.isRunning()); box.addView(check);
                LinearLayout texts=new LinearLayout(MainActivity.this); texts.setOrientation(LinearLayout.VERTICAL); box.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
                TextView head=new TextView(MainActivity.this); head.setText((s.address==null?"未知发件人":s.address)+"  "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(s.date))); head.setTextSize(15); texts.addView(head);
                TextView body=new TextView(MainActivity.this); body.setText(s.body); body.setMaxLines(4); texts.addView(body);
                check.setOnCheckedChangeListener((button,on)->{if(on)selected.add(s.id);else selected.remove(s.id); status.setText(rows.size()+" 条结果 · 已选 "+selected.size()+" 条");});
                texts.setOnClickListener(v->new AlertDialog.Builder(MainActivity.this).setTitle(s.address).setMessage(s.body).setPositiveButton("关闭",null).setNeutralButton("查此发件人",(d,w)->{sender.setText(s.address); keyword.setText(""); load();}).show()); return box;
            }
        }; list.setAdapter(adapter);
        delete=new Button(this); delete.setText("删除所选短信"); root.addView(delete); delete.setOnClickListener(v->confirmDelete());
        Button defaults=new Button(this); defaults.setText("打开设置，手动恢复原短信应用"); root.addView(defaults); defaults.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)));
        setContentView(root);
        if(Intent.ACTION_SENDTO.equals(getIntent().getAction())) new AlertDialog.Builder(this).setTitle("仅用于清理").setMessage("此版本不支持编写或发送短信。请先恢复原短信应用，再发送短信。").setPositiveButton("打开设置",(d,w)->startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))).setNegativeButton("关闭",null).show();
    }
    @Override public void onResume(){super.onResume(); if(keyword!=null){SafetyState.refreshNotification(this);refreshSafety();load();ui.removeCallbacks(monitor);ui.post(monitor);}}
    @Override public void onPause(){super.onPause();ui.removeCallbacks(monitor);}
    void refreshSafety(){
        recovery.setText(SafetyState.guidance(this));
        String event=SafetyState.prefs(this).getString("last_event", "");
        operation.setText(CleanupTask.summary(this)+(event.isEmpty()?"":"\n接收异常记录："+event));
        delete.setEnabled(!busy&&!CleanupTask.isRunning());
    }
    void addButton(LinearLayout row,String text,View.OnClickListener action){Button b=new Button(this);b.setText(text);row.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(action);}
    boolean isDefault(){return getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(this));}
    void update(){status.setText(rows.size()+" 条结果 · 已选 "+selected.size()+" 条"+(busy?" · 处理中":"")+(isDefault()?"\n⚠ 当前为默认短信应用，清理后请立即恢复原应用":""));adapter.notifyDataSetChanged();refreshSafety();}
    void error(String text){new AlertDialog.Builder(this).setTitle("提示").setMessage(text).setPositiveButton("知道了",null).show();}
    void load(){
        if(busy||CleanupTask.isRunning())return;
        if(checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED){status.setText("请允许读取短信以查询；读取不需要默认短信角色。");requestPermissions(Build.VERSION.SDK_INT>=33 ? new String[]{Manifest.permission.READ_SMS, Manifest.permission.POST_NOTIFICATIONS} : new String[]{Manifest.permission.READ_SMS},10);return;}
        final int token=++generation; final String k=keyword.getText().toString(), a=sender.getText().toString(); busy=true; selected.clear(); update();
        worker.execute(()->{ArrayList<Sms> found=new ArrayList<>();String failure=null;
            try(Cursor c=getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id","address","body","date","type"},"COALESCE(body,'') LIKE ? ESCAPE '\\' AND COALESCE(address,'') LIKE ? ESCAPE '\\'",new String[]{Filters.pattern(k),Filters.pattern(a)},"date DESC, _id DESC")){if(c!=null)while(c.moveToNext())found.add(new Sms(c));else failure="短信服务未返回数据，请重试。";}
            catch(Exception e){failure="读取失败："+e.getClass().getSimpleName();}
            final String message=failure; runOnUiThread(()->{if(isDestroyed()||token!=generation)return;busy=false;rows.clear();rows.addAll(found);update();if(message!=null)error(message);});
        });
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==10&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)load();}
    void confirmDelete(){
        if(busy||CleanupTask.isRunning()||selected.isEmpty()){error("请先选择要删除的短信。");return;}
        if(!isDefault()){
            new AlertDialog.Builder(this).setTitle("删除需要临时默认短信权限").setMessage("Android 只允许默认短信应用删除短信。此工具不是完整短信客户端：不支持彩信（MMS）、RCS、发送短信和通话快捷回复。默认期间这些功能可能失效，彩信可能无法接收。\n\n请先开启飞行模式，在原短信应用备份重要内容，完成清理后立即恢复原短信应用，再关闭飞行模式。本工具会尝试保存普通 SMS，但无法保证收信不中断。彩信不会下载或保存，恢复原应用也不保证补收。退出本工具不会自动恢复默认应用。\n\n继续仅申请角色，不会立即删除。")
            .setNegativeButton("取消",null).setPositiveButton("继续申请",(d,w)->{
                if(Settings.Global.getInt(getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)!=1){error("为降低接收风险，请先开启飞行模式，再继续。此工具不会代你修改设置。");return;}
                SafetyState.rememberOriginal(this); RoleManager rm=getSystemService(RoleManager.class);if(rm!=null&&rm.isRoleAvailable(RoleManager.ROLE_SMS))startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_SMS),20);else error("系统没有提供默认短信角色。请检查 OPPO 默认应用设置。");
            }).show();return;
        }
        if(Settings.Global.getInt(getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)!=1){error("请先开启飞行模式，清理后恢复原短信应用，再关闭飞行模式。");return;}
        final ArrayList<Long> ids=new ArrayList<>(selected);
        new AlertDialog.Builder(this).setTitle("永久删除 "+ids.size()+" 条短信？").setMessage("只删除当前已选短信，不删除整个会话。系统删除无法撤销。建议先点击“备份所选”保存 JSON。短信备份包含私人内容，请妥善保管。")
        .setNegativeButton("取消",null).setPositiveButton("永久删除",(d,w)->{
            if (!isDefault() || Settings.Global.getInt(getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)!=1) {
                error("默认角色或飞行模式已改变，未开始删除。请先恢复安全设置。"); return;
            }
            if (!CleanupTask.start(this, ids)) { error("已有清理正在运行，或无法保存操作日志。未启动新的删除。"); return; }
            observedRunning=true; selected.clear(); update();

        }).show();
    }
    void export(){
        if(busy||CleanupTask.isRunning()||selected.isEmpty()){error("请先选择要备份的短信。");return;}
        exportSnapshot=new ArrayList<>();for(Sms s:rows)if(selected.contains(s.id))exportSnapshot.add(s);
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"sms-backup-"+System.currentTimeMillis()+".json"),30);
    }
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==30&&result==RESULT_OK&&data!=null&&data.getData()!=null&&exportSnapshot!=null){ final Uri target=data.getData(); final ArrayList<Sms> snapshot=exportSnapshot;exportSnapshot=null;worker.execute(()->{
            String message;try{JSONArray entries=new JSONArray();for(Sms s:snapshot){JSONObject j=new JSONObject();j.put("id",s.id);j.put("address",s.address);j.put("body",s.body);j.put("date",s.date);j.put("type",s.type);entries.put(j);}JSONObject root=new JSONObject();root.put("format","sms-manager-backup-v1");root.put("messages",entries);try(OutputStream out=getContentResolver().openOutputStream(target)){if(out==null)throw new IOException();out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));}message="已备份 "+snapshot.size()+" 条短信。JSON 用于保留和查看，本版本不提供恢复导入。";}catch(Exception e){message="备份失败，尚未确认保存成功："+e.getClass().getSimpleName();}final String msg=message;runOnUiThread(()->{if(!isDestroyed())error(msg);});
        });}
    }
    @Override public void onDestroy(){super.onDestroy();ui.removeCallbacks(monitor);worker.shutdown();}
}
