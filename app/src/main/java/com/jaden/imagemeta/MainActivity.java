package com.jaden.imagemeta;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.*;

public class MainActivity extends Activity {
    private static final int PICK=10,SAVE=11,IMPORT=12,GPS_PERMISSION=13;
    private static final int INK=0xff163b36,MUTED=0xff627971,TEAL=0xff207c72,BG=0xfff4f7f6;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String,EditText> fields=new LinkedHashMap<>();
    private final ArrayList<View> controls=new ArrayList<>();
    private LinearLayout root,content,details;private TextView status,fileTitle,summary,capability,previewHint,countText;
    private ImageView preview;private ProgressBar progress;private Button saveButton,convertButton,mapButton,resetButton;
    private EditText search;private Bitmap previewBitmap;private MetadataEngine.Photo photo;private Uri selectedUri;
    private File pending;private String pendingMime,pendingName;private boolean busy=false;private int generation=0,shown=100;
    private Bundle restoredFields;

    @Override public void onCreate(Bundle state){super.onCreate(state);buildUi();
        if(state!=null){restoredFields=state.getBundle("fields");String pendingPath=state.getString("pendingPath");if(pendingPath!=null){File candidate=new File(pendingPath);try{if(candidate.getCanonicalFile().getParentFile().equals(getCacheDir().getCanonicalFile())&&candidate.isFile()){pending=candidate;pendingMime=state.getString("pendingMime");pendingName=state.getString("pendingName");}}catch(IOException ignored){}}String u=state.getString("uri");if(u!=null){selectedUri=Uri.parse(u);load(selectedUri,false);}}
        else acceptIntent(getIntent());
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);acceptIntent(intent);}
    private void acceptIntent(Intent intent){if(intent==null)return;Uri uri=null;
        if(Intent.ACTION_SEND.equals(intent.getAction()))uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);
        else if(Intent.ACTION_VIEW.equals(intent.getAction()))uri=intent.getData();
        if(uri!=null)load(uri,false);
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);if(selectedUri!=null)out.putString("uri",selectedUri.toString());if(pending!=null){out.putString("pendingPath",pending.getAbsolutePath());out.putString("pendingMime",pendingMime);out.putString("pendingName",pendingName);}Bundle f=new Bundle();for(Map.Entry<String,EditText> e:fields.entrySet())f.putString(e.getKey(),e.getValue().getText().toString());out.putBundle("fields",f);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(3),1);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private void gap(LinearLayout parent,int size){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(size)));}
    private LinearLayout card(String title,String sub){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(rounded(Color.WHITE,22));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);content.addView(c,lp);c.addView(text(title,18,INK,true));if(sub!=null){gap(c,5);c.addView(text(sub,12,MUTED,false));}gap(c,12);return c;}
    private Button button(LinearLayout parent,String title,boolean primary,Runnable run){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?Color.WHITE:TEAL);b.setBackground(rounded(primary?TEAL:0xffe8f2ef,14));b.setMinHeight(dp(48));b.setPadding(dp(12),dp(8),dp(12),dp(8));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);parent.addView(b,lp);b.setOnClickListener(v->run.run());controls.add(b);return b;}
    private EditText field(LinearLayout parent,String key,String label,String hint,boolean numeric){TextView t=text(label,13,MUTED,true);LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,-2);tl.topMargin=dp(12);parent.addView(t,tl);EditText e=new EditText(this);e.setTextSize(15);e.setTextColor(INK);e.setHintTextColor(0xff8a9b95);e.setHint(hint);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(rounded(0xfff3f7f5,10));e.setSingleLine(!key.equals("note"));if(key.equals("note"))e.setMinLines(2);e.setInputType(numeric?android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED:android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|(key.equals("note")?android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));LinearLayout.LayoutParams el=new LinearLayout.LayoutParams(-1,-2);el.topMargin=dp(6);parent.addView(e,el);fields.put(key,e);controls.add(e);return e;}
    private void buildUi(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});setContentView(root);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,-1));content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(16),dp(16),dp(16),dp(28));scroll.addView(content);
        TextView badge=text("IMAGE META  ·  本地处理",11,TEAL,true);content.addView(badge);gap(content,6);content.addView(text("图片元数据",30,INK,true));gap(content,5);content.addView(text("查看图片的信息，编辑它的故事。",14,MUTED,false));gap(content,14);
        LinearLayout open=card("打开一张图片","支持从系统文件管理器选择，也可从相册分享至本应用。");
        button(open,"选择图片",true,()->pick(false));button(open,"选择其他格式 / RAW 文件",false,()->pick(true));
        status=text("所有处理在手机上进行 · 无广告 · 不上传图片",12,MUTED,false);open.addView(status);progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(View.GONE);open.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));
        LinearLayout basic=card("图片概览",null);fileTitle=text("尚未选择图片",17,INK,true);basic.addView(fileTitle);gap(basic,8);preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackground(rounded(0xfff0f5f2,14));preview.setContentDescription("所选图片预览");basic.addView(preview,new LinearLayout.LayoutParams(-1,dp(210)));previewHint=text("选择后显示预览；预览失败不会阻止元数据读取。",12,MUTED,false);gap(basic,8);basic.addView(previewHint);summary=text("格式、尺寸、文件大小与文件位置",13,MUTED,false);summary.setTextIsSelectable(true);gap(basic,8);basic.addView(summary);capability=text("JPG / PNG / WebP 可直接写入元数据副本。",12,TEAL,true);gap(basic,8);basic.addView(capability);
        button(basic,"查看格式兼容说明",false,this::showSupport);
        LinearLayout gps=card("拍摄位置","经纬度使用 WGS-84。地址是手动备注；应用不会联网查询地址。");
        field(gps,"latitude","纬度","-90 ～ 90，例如 31.2304",true);field(gps,"longitude","经度","-180 ～ 180，例如 121.4737",true);field(gps,"address","地点 / 地址","例如 上海市黄浦区……",false);
        mapButton=button(gps,"在地图中查看填写的坐标",false,this::openMap);
        button(gps,"清空 EXIF 经纬度",false,()->{fields.get("latitude").setText("");fields.get("longitude").setText("");toast("已清空编辑字段，保存副本后生效。");});
        button(gps,"尝试读取原始 GPS",false,this::requestOriginalGps);
        gps.addView(text("未显示 GPS 不代表从未记录：原图可能没有坐标，也可能被系统或分享应用隐藏。改变坐标会删除旧海拔等 GPS 附属字段；清空仅修改 EXIF GPS，XMP 或厂商字段可能仍记录位置。",11,MUTED,false));
        LinearLayout edit=card("编辑信息","留空会删除对应字段。地点、来源网址和备注保存于本应用的 EXIF UserComment；其他软件可能显示为结构化文本。");
        field(edit,"DateTimeOriginal","拍摄时间","2026:10:13 15:30:00",false);field(edit,"DateTime","图片修改时间","yyyy:MM:dd HH:mm:ss",false);
        field(edit,"Make","设备厂商","例如 HONOR、Apple、Canon",false);field(edit,"Model","设备型号","例如 PGT-AN10",false);field(edit,"Artist","作者","EXIF 标准作者字段",false);field(edit,"Copyright","版权","EXIF 标准版权字段",false);field(edit,"Software","处理软件","例如 Camera",false);
        field(edit,"sourceUrl","来源网址","https://…（需要手动填写）",false);field(edit,"note","备注","拍摄说明或其他信息",false);
        LinearLayout save=card("保存与导出","原图始终保留。保存前会检查字段，写入后会重新读取校验。");
        saveButton=button(save,"保存元数据副本",true,()->prepare("copy"));
        convertButton=button(save,"转换为 PNG 副本并写入",false,this::confirmConvert);
        button(save,"导出信息文件（JSON）",false,()->prepare("json"));
        button(save,"导入此前的信息文件",false,this::importJson);
        resetButton=button(save,"恢复原图中的编辑字段",false,()->{if(photo==null)return;new AlertDialog.Builder(this).setMessage("恢复原图字段会丢弃当前尚未保存的编辑。").setPositiveButton("恢复",(d,w)->populate(photo.values)).setNegativeButton("取消",null).show();});
        save.addView(text("信息文件适用于所有已打开的格式；它与图片独立，不会改变原图的内嵌元数据。转换 PNG 会保留完整尺寸与透明度，但不保留 RAW 特性、HDR 或全部原始元数据。",11,MUTED,false));
        LinearLayout all=card("全部可读取的信息","EXIF · GPS · XMP · IPTC · 格式专有信息。长按文字可复制。");search=new EditText(this);search.setSingleLine();search.setHint("搜索，例如 GPS、ISO、Lens、地址");search.setTextSize(14);all.addView(search);countText=text("选择图片后显示",12,MUTED,false);all.addView(countText);details=new LinearLayout(this);details.setOrientation(LinearLayout.VERTICAL);all.addView(details);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){shown=100;renderTags();}public void afterTextChanged(Editable e){}});
        TextView footer=text("图片元数据 1.0  ·  Android 8.0 及以上\n文件位置由系统提供，可能是 content:// URI。来源网址通常不会由图片自动记录。",11,MUTED,false);content.addView(footer);updateControls();
    }
    private void pick(boolean any){if(busy)return;if(isDirty()){new AlertDialog.Builder(this).setMessage("打开其他图片会丢弃当前尚未保存的编辑。").setPositiveButton("继续打开",(d,w)->launchPicker(any)).setNegativeButton("取消",null).show();}else launchPicker(any);}
    private void launchPicker(boolean any){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(any?"*/*":"image/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK);}
    private boolean isDirty(){if(photo==null)return false;Map<String,String> current=snapshot();for(String key:current.keySet())if(!current.get(key).equals(photo.values.getOrDefault(key,"").trim()))return true;return false;}
    private Map<String,String> snapshot(){LinkedHashMap<String,String> v=new LinkedHashMap<>();for(Map.Entry<String,EditText> e:fields.entrySet())v.put(e.getKey(),e.getValue().getText().toString().trim());return v;}
    private void populate(Map<String,String> values){for(Map.Entry<String,EditText> e:fields.entrySet())e.getValue().setText(values.getOrDefault(e.getKey(),""));}
    private void setBusy(boolean b,String message){busy=b;status.setText(message);progress.setVisibility(b?View.VISIBLE:View.GONE);updateControls();}
    private void updateControls(){for(View v:controls)v.setEnabled(!busy);for(EditText v:fields.values())v.setEnabled(!busy&&photo!=null);if(saveButton!=null)saveButton.setEnabled(!busy&&photo!=null&&photo.writable);if(convertButton!=null)convertButton.setEnabled(!busy&&photo!=null&&!photo.animated);if(mapButton!=null)mapButton.setEnabled(!busy&&photo!=null);if(resetButton!=null)resetButton.setEnabled(!busy&&photo!=null);}
    private void load(Uri uri,boolean original){if(busy)return;int id=++generation;setBusy(true,"正在读取图片与元数据…");worker.execute(()->{
        File staged=null;Bitmap bitmap=null;
        try{
            String name="image";try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()){int n=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(n>=0)name=c.getString(n);int s=c.getColumnIndex(OpenableColumns.SIZE);if(s>=0&&!c.isNull(s)&&c.getLong(s)>MetadataEngine.MAX_BYTES)throw new IOException("文件超过 512 MB，暂不支持。");}}
            Uri readUri=uri;boolean originalUnavailable=false;
            if(Build.VERSION.SDK_INT>=29&&(original||checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)==PackageManager.PERMISSION_GRANTED)){
                try{Uri media=MediaStore.getMediaUri(this,uri);if(media!=null)readUri=MediaStore.setRequireOriginal(media);else if("media".equals(uri.getAuthority()))readUri=MediaStore.setRequireOriginal(uri);}catch(Exception e){originalUnavailable=original;}
            }
            staged=File.createTempFile("source-",".img",getCacheDir());
            try(InputStream in=getContentResolver().openInputStream(readUri);OutputStream out=new FileOutputStream(staged)){if(in==null)throw new IOException("无法打开文件。");MetadataEngine.copy(in,out);}
            catch(SecurityException|UnsupportedOperationException e){if(readUri.equals(uri))throw e;originalUnavailable=true;try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(staged)){if(in==null)throw new IOException("无法打开文件。");MetadataEngine.copy(in,out);}}
            MetadataEngine.Photo p=MetadataEngine.read(staged,name,getContentResolver().getType(uri),uri.toString());
            try{bitmap=MetadataEngine.preview(p);}catch(Exception|OutOfMemoryError e){/* Metadata stays usable even without a decoder. */}
            Bitmap result=bitmap;boolean gpsLimited=originalUnavailable;
            runOnUiThread(()->{if(isDestroyed()||id!=generation){p.file.delete();if(result!=null)result.recycle();return;}if(photo!=null)photo.file.delete();if(previewBitmap!=null)previewBitmap.recycle();photo=p;selectedUri=uri;previewBitmap=result;preview.setImageBitmap(result);populate(p.values);
                if(restoredFields!=null){for(String key:restoredFields.keySet())if(fields.containsKey(key))fields.get(key).setText(restoredFields.getString(key,""));restoredFields=null;}
                fileTitle.setText(p.name);previewHint.setText(result==null?"手机未能解码预览；元数据仍可查看。":p.animated?"动图仅显示预览帧；直接保存支持的格式时保留动画。":"预览已按拍摄方向显示。");
                summary.setText(p.format+"  ·  "+(p.width>0?p.width+" × "+p.height:"尺寸见详细元数据")+"  ·  "+String.format(Locale.US,"%.2f MB",p.file.length()/1048576.0)+"\n文件位置："+p.uri);
                capability.setText(p.writable?"可直接写入 EXIF · 保存同格式副本"+(p.animated?" · 保留动画":""):"内嵌元数据只读 · 可编辑并导出信息文件"+(p.animated?"":" / 尝试转换 PNG"));
                shown=100;renderTags();setBusy(false,gpsLimited?"系统未允许读取原始 GPS；当前显示可访问的信息。":p.warning.isEmpty()?"读取完成 · 原图保留":p.warning);
            });
        }catch(Exception|OutOfMemoryError e){if(staged!=null)staged.delete();if(bitmap!=null)bitmap.recycle();runOnUiThread(()->{if(!isDestroyed()&&id==generation){setBusy(false,"读取失败");error(e);}});}
    });}
    private void requestOriginalGps(){if(photo==null){toast("先选择图片。");return;}if(Build.VERSION.SDK_INT<29){load(selectedUri,true);return;}
        new AlertDialog.Builder(this).setMessage("读取完整 GPS 可能需要照片读取及媒体位置信息权限。这里只读取图片中已记录的位置，不获取手机当前位置。重新读取会恢复原图字段。").setPositiveButton("继续",(d,w)->{
            ArrayList<String> permissions=new ArrayList<>();if(checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.ACCESS_MEDIA_LOCATION);
            String read=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE;if(checkSelfPermission(read)!=PackageManager.PERMISSION_GRANTED)permissions.add(read);
            if(permissions.isEmpty())load(selectedUri,true);else requestPermissions(permissions.toArray(new String[0]),GPS_PERMISSION);
        }).setNegativeButton("取消",null).show();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);if(code==GPS_PERMISSION&&selectedUri!=null)load(selectedUri,true);}
    private void openMap(){try{double[] ll=EditValues.coordinates(fields.get("latitude").getText().toString(),fields.get("longitude").getText().toString());if(ll==null){toast("先填写经纬度。");return;}String pair=ll[0]+","+ll[1];Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:"+pair+"?q="+Uri.encode(pair+" (拍摄位置，WGS-84)")));startActivity(i);}catch(ActivityNotFoundException e){toast("手机没有可处理此坐标的地图应用。");}catch(Exception e){error(e);}}
    private void confirmConvert(){if(photo==null){toast("先选择图片。");return;}new AlertDialog.Builder(this).setTitle("创建 PNG 副本").setMessage("转换会使用完整尺寸，不覆盖原图。它可能增大文件，且不保留 RAW 特性、HDR、原有图层及全部元数据。仅当前编辑字段写入新副本；手机无法解码时会停止。").setPositiveButton("转换并保存",(d,w)->prepare("png")).setNegativeButton("取消",null).show();}
    private void prepare(String kind){if(busy)return;if(photo==null){toast("先选择图片。");return;}Map<String,String> values=snapshot();try{EditValues.validate(values);}catch(Exception e){error(e);return;}
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);MetadataEngine.Photo current=photo;setBusy(true,"正在准备副本并校验…");worker.execute(()->{File file=null;try{
            String base=current.name.replaceFirst("\\.[^.]+$","");String mime,name;
            if(kind.equals("json")){file=File.createTempFile("report-",".json",getCacheDir());try(Writer out=new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8)){out.write(MetadataEngine.report(current,values).toString(2));}mime="application/json";name=base+".metadata.json";}
            else if(kind.equals("png")){file=MetadataEngine.convertPng(current,values,getCacheDir());mime="image/png";name=base+"_edited.png";}
            else{file=MetadataEngine.writeCopy(current,values,getCacheDir());mime=current.mime;name=base+"_edited."+("JPEG".equals(current.format)?"jpg":"PNG".equals(current.format)?"png":"webp");}
            if(!MetadataEngine.hash(current.file).equals(current.hash))throw new IOException("原始缓存校验失败，已停止保存。");
            File ready=file;String type=mime,title=name;runOnUiThread(()->{if(isDestroyed()){ready.delete();return;}if(pending!=null)pending.delete();pending=ready;pendingMime=type;pendingName=title;setBusy(false,"副本已准备好，请选择保存位置。");Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(type);i.putExtra(Intent.EXTRA_TITLE,title);try{startActivityForResult(i,SAVE);}catch(Exception e){pending.delete();pending=null;error(e);}});
        }catch(Exception|OutOfMemoryError e){if(file!=null)file.delete();runOnUiThread(()->{if(!isDestroyed()){setBusy(false,"未保存："+message(e));error(e);}});}});
    }
    private void importJson(){if(photo==null){toast("先选择对应的图片。");return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,IMPORT);}
    private void readJson(Uri uri){if(busy||photo==null)return;MetadataEngine.Photo current=photo;setBusy(true,"正在读取信息文件…");worker.execute(()->{try{ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法读取信息文件。");byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>16*1024*1024)throw new IOException("信息文件超过 16 MB。");out.write(b,0,n);}}
            JSONObject j=new JSONObject(out.toString("UTF-8"));if(!"ImageMeta/1".equals(j.optString("schema")))throw new IOException("这不是本应用的信息文件。");if(!current.hash.equals(j.optString("originalSha256")))throw new IOException("信息文件与当前图片不匹配。请先打开导出时的原图。");JSONObject f=j.getJSONObject("editedFields");Map<String,String> v=new LinkedHashMap<>();for(String key:current.values.keySet())v.put(key,f.optString(key,""));EditValues.validate(v);runOnUiThread(()->{if(!isDestroyed()){populate(v);setBusy(false,"已导入编辑字段；尚未写入图片。");}});
        }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){setBusy(false,"导入失败");error(e);}});}});
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null){if(request==SAVE&&pending!=null){pending.delete();pending=null;status.setText("已取消保存，原图保留。");}return;}Uri uri=data.getData();
        if(request==PICK){try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}load(uri,false);}
        else if(request==IMPORT)readJson(uri);
        else if(request==SAVE&&pending!=null){if(uri.equals(selectedUri)){pending.delete();pending=null;error(new IOException("请保存到新的文件，避免覆盖原图。"));return;}File ready=pending;pending=null;setBusy(true,"正在保存并核对文件…");worker.execute(()->{try{
            String expected=MetadataEngine.hash(ready);try(InputStream in=new FileInputStream(ready);OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法写入所选位置。");MetadataEngine.copy(in,out);out.flush();}
            // Read-back verification is essential: a provider may truncate or transform writes.
            File check=File.createTempFile("verify-",".img",getCacheDir());try{try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(check)){if(in==null)throw new IOException("保存后无法重新读取，结果尚未验证。");MetadataEngine.copy(in,out);}if(!expected.equals(MetadataEngine.hash(check)))throw new IOException("保存后的文件内容校验失败，请勿使用该副本。");}finally{check.delete();}
            runOnUiThread(()->{if(!isDestroyed()){setBusy(false,"已保存并校验 · "+pendingName);new AlertDialog.Builder(this).setTitle("保存成功").setMessage("副本已保存到：\n"+uri+"\n\n原图保留。").setPositiveButton("完成",null).show();}});
        }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()){setBusy(false,"保存未完成："+message(e));error(e);}});}finally{ready.delete();}});}
    }
    private void renderTags(){if(details==null)return;details.removeAllViews();if(photo==null){countText.setText("选择图片后显示");return;}String q=search.getText().toString().toLowerCase(Locale.ROOT).trim();int matched=0,rendered=0;String group="";
        for(int i=0;i<photo.tags.length();i++){JSONObject t=photo.tags.optJSONObject(i);String g=t.optString("group"),key=t.optString("tag"),value=t.optString("value");if(!(g+" "+key+" "+value).toLowerCase(Locale.ROOT).contains(q))continue;matched++;if(rendered>=shown)continue;rendered++;
            if(!group.equals(g)){gap(details,16);details.addView(text(g,13,TEAL,true));group=g;}gap(details,10);TextView label=text(key,12,MUTED,true);details.addView(label);TextView v=text(value.length()>1600?value.substring(0,1600)+"…（完整内容见 JSON 导出）":value,13,INK,false);v.setTextIsSelectable(true);details.addView(v);
        }countText.setText(matched==0?"未找到匹配的元数据。" : "共 "+matched+" 项 · 当前显示 "+rendered+" 项");
        if(matched>shown){Button more=new Button(this);more.setText("再显示 100 项");more.setOnClickListener(v->{shown+=100;renderTags();});details.addView(more);}
    }
    private void showSupport(){new AlertDialog.Builder(this).setTitle("格式兼容范围").setMessage("直接编辑并保存同格式副本\nJPEG / JPG、PNG、WebP。修改元数据时不重新压缩像素；支持的 APNG / WebP 动画会保留。\n\n读取元数据\nHEIC / HEIF、AVIF、TIFF、GIF、BMP、PSD、ICO、PCX，以及 DNG、CR2、NEF、NRW、ARW、RW2、ORF、PEF、SRW、RAF 等 RAW。具体可读字段依赖文件和解析器，损坏或特殊编码可能无法解析；AVIF 的 EXIF 读取需要 Android 12+。\n\n预览 / PNG 转换\n取决于手机解码能力；TIFF / RAW / PSD 等可能只能查看信息或内嵌缩略图，无法转换。所有 GIF 保守地禁用静态转换；动图不会被自动展平。SVG、JPEG XL、CR3 等未列出的格式不保证支持。\n\n统一信息文件\n各格式都可编辑字段并导出 JSON，随后在本应用导入；这些编辑独立于图片。标准文本字段可能受 EXIF 字符编码限制，写入失败会提示。\n\n边界\n原图最大 512 MB；PNG 转换最大 3600 万像素。地址及来源网址不是所有图片都有的信息，应用不会凭空推断。GPS 清空只处理 EXIF 字段，不是彻底隐私清理。").setPositiveButton("知道了",null).show();}
    private static String message(Throwable e){String s=e.getMessage();return s==null?e instanceof OutOfMemoryError?"内存不足，请选择较小的图片。":e.getClass().getSimpleName():s;}
    private void error(Throwable e){new AlertDialog.Builder(this).setTitle("暂未完成").setMessage(message(e)).setPositiveButton("知道了",null).show();}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){super.onDestroy();generation++;File source=photo==null?null:photo.file;File export=isChangingConfigurations()?null:pending;worker.execute(()->{if(source!=null)source.delete();if(export!=null)export.delete();});worker.shutdown();if(previewBitmap!=null)previewBitmap.recycle();}
}
