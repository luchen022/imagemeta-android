package com.jaden.imagemeta;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.os.Build;
import androidx.exifinterface.media.ExifInterface;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import java.io.*;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MetadataEngine {
    public static final long MAX_BYTES = 512L * 1024 * 1024;
    public static final String COMMENT_PREFIX = "ImageMeta/1 ";
    public static final String[] STANDARD = {"DateTimeOriginal", "DateTime", "Make", "Model", "Artist", "Copyright", "Software"};
    public static final class Photo {
        File file; String name, mime, uri, format, hash, warning = ""; int width, height;
        boolean writable, animated; ExifInterface exif;
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        JSONArray tags = new JSONArray();
    }
    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024]; long total = 0; int n;
        while ((n = in.read(buffer)) != -1) { total += n; if (total > MAX_BYTES) throw new IOException("文件超过 512 MB，暂不支持。"); out.write(buffer, 0, n); }
    }
    public static String hash(File file) throws Exception {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) { byte[] b = new byte[65536]; int n; while ((n=in.read(b))!=-1) d.update(b, 0, n); }
        StringBuilder s = new StringBuilder(); for (byte b : d.digest()) s.append(String.format(Locale.US, "%02x", b & 255)); return s.toString();
    }
    public static Photo read(File file, String name, String mime, String uri) throws Exception {
        Photo p = new Photo(); p.file = file; p.name = name; p.mime = mime == null ? "application/octet-stream" : mime; p.uri = uri; p.hash = hash(file);
        byte[] h = new byte[64]; int count; try(InputStream in = new FileInputStream(file)){count=in.read(h);}
        String magic = count > 0 ? new String(h, 0, count, java.nio.charset.StandardCharsets.ISO_8859_1) : "";
        if(count>=3 && (h[0]&255)==255 && (h[1]&255)==216 && (h[2]&255)==255) {p.format="JPEG"; p.mime="image/jpeg"; p.writable=true;}
        else if(count>=8 && h[0]==(byte)137 && magic.substring(1,4).equals("PNG")){p.format="PNG";p.mime="image/png";p.writable=true;p.animated=pngAnimated(file);}
        else if(count>=12 && magic.startsWith("RIFF") && magic.substring(8,12).equals("WEBP")){p.format="WebP";p.mime="image/webp";p.writable=true;p.animated=webpAnimated(file);}
        else if(magic.startsWith("GIF8")){p.format="GIF";p.mime="image/gif";p.animated=true;}
        else if(magic.startsWith("BM")){p.format="BMP";p.mime="image/bmp";}
        else if(magic.startsWith("II") || magic.startsWith("MM")){p.format="TIFF / RAW";}
        else if(magic.startsWith("8BPS")){p.format="PSD";}
        else if(magic.contains("ftyp")){p.format=magic.contains("avif")||magic.contains("avis")?"AVIF":"HEIC / HEIF / ISO-BMFF";p.animated=magic.contains("avis")||magic.contains("msf1");}
        else {String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toUpperCase(Locale.US):"未知";p.format=ext;}
        try { p.exif = new ExifInterface(file); }
        catch (Exception e) { p.warning="此格式的 EXIF 解析不可用；仍会尝试读取其他元数据。"; }
        if(p.exif!=null) {
            for(String key:STANDARD)p.values.put(key, val(p.exif.getAttribute(key)));
            double[] ll=p.exif.getLatLong(); p.values.put("latitude",ll==null?"":coordinateText(ll[0]));p.values.put("longitude",ll==null?"":coordinateText(ll[1]));
            String comment=val(p.exif.getAttribute(ExifInterface.TAG_USER_COMMENT));
            p.values.put("address", "");p.values.put("sourceUrl", "");p.values.put("note", comment);
            if(comment.startsWith(COMMENT_PREFIX))try{
                JSONObject custom=new JSONObject(comment.substring(COMMENT_PREFIX.length()));
                for(String key:new String[]{"address","sourceUrl","note"})p.values.put(key,custom.optString(key,""));
            }catch(Exception ignored){}
            for(Field f:ExifInterface.class.getFields())if(f.getName().startsWith("TAG_") && f.getType()==String.class)try{
                String key=(String)f.get(null);String value=p.exif.getAttribute(key);if(value!=null)addTag(p,"EXIF / XMP",key,value);
            }catch(Exception ignored){}
        }
        try {
            Metadata m=ImageMetadataReader.readMetadata(file);
            for(Directory d:m.getDirectories()){
                for(Tag tag:d.getTags())addTag(p,d.getName(),tag.getTagName(),val(tag.getDescription()));
                for(String error:d.getErrors())addTag(p,"解析提示",d.getName(),error);
            }
        }catch(Exception e){ if(p.tags.length()==0)p.warning="未找到可解析的内嵌元数据，或此格式尚不受支持。"; }
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);p.width=Math.max(0,o.outWidth);p.height=Math.max(0,o.outHeight);
        if(p.exif!=null){if(p.width==0)p.width=p.exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH,0);if(p.height==0)p.height=p.exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH,0);}
        for(String key:STANDARD)p.values.putIfAbsent(key,"");for(String key:new String[]{"latitude","longitude","address","sourceUrl","note"})p.values.putIfAbsent(key,"");
        return p;
    }
    private static String val(String s){return s==null?"":s;}
    private static String coordinateText(double value){return java.math.BigDecimal.valueOf(value).setScale(8,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
    private static void addTag(Photo p,String group,String key,String value)throws Exception{
        if(p.tags.length()>=3500)return;JSONObject j=new JSONObject();j.put("group",group);j.put("tag",key);j.put("value",value.length()>32768?value.substring(0,32768)+"…（已截断）":value);p.tags.put(j);
    }
    private static boolean pngAnimated(File file)throws IOException{
        try(RandomAccessFile f=new RandomAccessFile(file,"r")){f.seek(8);while(f.getFilePointer()+12<=f.length()){
            long n=Integer.toUnsignedLong(f.readInt());byte[] type=new byte[4];f.readFully(type);String t=new String(type,java.nio.charset.StandardCharsets.US_ASCII);
            if(t.equals("acTL"))return true;if(n>f.length()-f.getFilePointer()-4)break;f.seek(f.getFilePointer()+n+4);if(t.equals("IEND"))break;
        }}return false;
    }
    private static boolean webpAnimated(File file)throws IOException{
        try(RandomAccessFile f=new RandomAccessFile(file,"r")){f.seek(12);while(f.getFilePointer()+8<=f.length()){
            byte[] type=new byte[4];f.readFully(type);long n=Integer.toUnsignedLong(Integer.reverseBytes(f.readInt()));String t=new String(type,java.nio.charset.StandardCharsets.US_ASCII);
            if(t.equals("ANIM")||t.equals("ANMF"))return true;if(n>f.length()-f.getFilePointer())break;f.seek(f.getFilePointer()+n+(n&1));
        }}return false;
    }
    public static Bitmap preview(Photo p)throws Exception{
        if(Build.VERSION.SDK_INT>=28)try{
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(p.file),(decoder,info,source)->{
                int w=info.getSize().getWidth(),h=info.getSize().getHeight();double s=Math.min(1.0,1400.0/Math.max(w,h));decoder.setTargetSize(Math.max(1,(int)(w*s)),Math.max(1,(int)(h*s)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        }catch(Exception ignored){}
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(p.file.getPath(),o);o.inSampleSize=1;
        while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>1400)o.inSampleSize*=2;o.inJustDecodeBounds=false;
        Bitmap b=BitmapFactory.decodeFile(p.file.getPath(),o);
        if(b==null && p.exif!=null)b=p.exif.getThumbnailBitmap();
        if(b!=null && p.exif!=null){android.graphics.Matrix m=new android.graphics.Matrix();if(p.exif.isFlipped())m.postScale(-1,1);m.postRotate(p.exif.getRotationDegrees());Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(rotated!=b)b.recycle();b=rotated;}
        return b;
    }
    public static File writeCopy(Photo p, Map<String,String> values, File directory)throws Exception{
        if(!p.writable)throw new IOException("当前格式只能查看。请导出信息文件，或转换 PNG 副本后编辑。");
        EditValues.validate(values);File out=File.createTempFile("edited-",".img",directory);
        try{try(InputStream in=new FileInputStream(p.file);OutputStream o=new FileOutputStream(out)){copy(in,o);}writeAttributes(out,values);return out;}
        catch(Exception e){out.delete();throw e;}
    }
    private static String emptyToNull(String s){return s==null||s.trim().isEmpty()?null:s.trim();}
    static void writeAttributes(File out,Map<String,String> values)throws Exception{
        ExifInterface exif=new ExifInterface(out);
        for(String key:STANDARD)exif.setAttribute(key,emptyToNull(values.get(key)));
        double[] ll=EditValues.coordinates(values.getOrDefault("latitude",""),values.getOrDefault("longitude",""));
        double[] before=exif.getLatLong();
        boolean changed=ll==null || before==null || Math.abs(ll[0]-before[0])>0.00000001 || Math.abs(ll[1]-before[1])>0.00000001;
        if(changed){
            for(Field f:ExifInterface.class.getFields())if(f.getName().startsWith("TAG_GPS_")&&f.getType()==String.class)exif.setAttribute((String)f.get(null),null);
            if(ll!=null){exif.setLatLong(ll[0],ll[1]);exif.setAttribute(ExifInterface.TAG_GPS_MAP_DATUM,"WGS-84");}
        }
        JSONObject custom=new JSONObject();boolean hasCustom=false;
        for(String key:new String[]{"address","sourceUrl","note"}){String value=values.getOrDefault(key,"").trim();custom.put(key,value);hasCustom|=!value.isEmpty();}
        String text=COMMENT_PREFIX+ascii(custom.toString());
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT,hasCustom?text:null);exif.saveAttributes();
        ExifInterface verify=new ExifInterface(out);double[] actual=verify.getLatLong();
        if(ll==null && actual!=null)throw new IOException("GPS 删除校验失败。");
        if(ll!=null&&(actual==null||Math.abs(ll[0]-actual[0])>0.000002||Math.abs(ll[1]-actual[1])>0.000002))throw new IOException("GPS 写入校验失败。");
        for(String key:STANDARD)if(!val(verify.getAttribute(key)).equals(values.getOrDefault(key,"").trim()))throw new IOException("字段写入校验失败："+key+"。此字段可能不支持当前字符。");
        if(hasCustom&&!text.equals(verify.getAttribute(ExifInterface.TAG_USER_COMMENT)))throw new IOException("备注写入校验失败。");
    }
    private static String ascii(String s){StringBuilder b=new StringBuilder();for(char c:s.toCharArray()){if(c>127)b.append(String.format(Locale.US,"\\u%04x",(int)c));else b.append(c);}return b.toString();}
    public static File convertPng(Photo p,Map<String,String> values,File directory)throws Exception{
        if(p.animated)throw new IOException("动图不提供静态转换，避免丢失动画。请导出信息文件。");
        EditValues.validate(values);Bitmap b=null;File out=File.createTempFile("converted-",".png",directory);
        try{
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(p.file.getPath(),bounds);
            if((long)bounds.outWidth*bounds.outHeight>36_000_000L)throw new IOException("图片超过 3600 万像素，暂不转换。可以导出信息文件。");
            if(Build.VERSION.SDK_INT>=28)b=ImageDecoder.decodeBitmap(ImageDecoder.createSource(p.file),(d,info,s)->{
                if((long)info.getSize().getWidth()*info.getSize().getHeight()>36_000_000L)throw new IllegalArgumentException("图片超过 3600 万像素，暂不转换。");d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
            else b=BitmapFactory.decodeFile(p.file.getPath());
            if(b==null)throw new IOException("手机无法完整解码此格式。仍可查看元数据、导出信息文件。");
            if(Build.VERSION.SDK_INT<28&&p.exif!=null){android.graphics.Matrix m=new android.graphics.Matrix();if(p.exif.isFlipped())m.postScale(-1,1);m.postRotate(p.exif.getRotationDegrees());Bitmap rotated=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),m,true);if(rotated!=b)b.recycle();b=rotated;}
            try(OutputStream o=new FileOutputStream(out)){if(!b.compress(Bitmap.CompressFormat.PNG,100,o))throw new IOException("PNG 编码失败。");}
            writeAttributes(out,values);return out;
        }catch(Exception e){out.delete();throw e;}finally{if(b!=null)b.recycle();}
    }
    public static JSONObject report(Photo p,Map<String,String> edited)throws Exception{
        JSONObject root=new JSONObject();root.put("schema","ImageMeta/1");root.put("name",p.name);root.put("format",p.format);root.put("mime",p.mime);root.put("width",p.width);root.put("height",p.height);root.put("sizeBytes",p.file.length());root.put("originalSha256",p.hash);root.put("originalUri",p.uri);root.put("embeddedMetadata",p.tags);root.put("editedFields",new JSONObject(edited));root.put("note","信息文件独立于图片；editedFields 尚未写入原图。GPS 使用 WGS-84，地址及来源网址为手动备注。");return root;
    }
}
