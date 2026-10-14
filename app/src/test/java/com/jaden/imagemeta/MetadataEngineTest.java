package com.jaden.imagemeta;

import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import androidx.exifinterface.media.ExifInterface;
import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=28)
public class MetadataEngineTest {
    private File temp;
    @Before public void setup()throws Exception{temp=Files.createTempDirectory("imagemeta-test").toFile();}
    @After public void cleanup(){for(File f:Objects.requireNonNull(temp.listFiles()))f.delete();temp.delete();}
    private File fixture(String name)throws Exception{File f=new File(temp,name);try(InputStream in=getClass().getResourceAsStream("/fixtures/"+name);OutputStream out=new FileOutputStream(f)){assertNotNull(in);MetadataEngine.copy(in,out);}return f;}
    private MetadataEngine.Photo read(String name)throws Exception{return MetadataEngine.read(fixture(name),name,null,"content://test/"+name);}
    private Map<String,String> edits(MetadataEngine.Photo p){Map<String,String> e=new LinkedHashMap<>(p.values);e.put("latitude","-31.2304");e.put("longitude","121.4737");e.put("DateTimeOriginal","2026:10:13 15:30:00");e.put("DateTime","2026:10:13 16:00:00");e.put("Make","HONOR");e.put("Model","PGT-AN10");e.put("Artist","Jaden");e.put("address","上海市黄浦区 中文地址");e.put("sourceUrl","https://example.com/photos/1");e.put("note","中文备注 📷\n第二行");return e;}
    @Test public void jpegPngWebpRoundTripAndPixelsRemainIdentical()throws Exception{
        for(String name:new String[]{"sample.jpg","sample.png","sample.webp"}){
            MetadataEngine.Photo p=read(name);assertTrue(p.writable);Map<String,String> e=edits(p);String original=p.hash;
            File saved=MetadataEngine.writeCopy(p,e,temp);MetadataEngine.Photo after=MetadataEngine.read(saved,name,p.mime,p.uri);
            assertEquals(e,after.values);assertEquals(original,MetadataEngine.hash(p.file));assertArrayEquals(payload(p.file,p.format),payload(saved,p.format));
            e.put("latitude","");e.put("longitude","");File cleared=MetadataEngine.writeCopy(after,e,temp);assertNull(new ExifInterface(cleared).getLatLong());assertEquals("",MetadataEngine.read(cleared,name,p.mime,p.uri).values.get("latitude"));
        }
    }
    @Test public void animationFramesAndTimingRemainIdentical()throws Exception{
        for(String name:new String[]{"animated.png","animated.webp"}){
            MetadataEngine.Photo p=read(name);assertTrue(p.animated);assertTrue(p.writable);File saved=MetadataEngine.writeCopy(p,edits(p),temp);MetadataEngine.Photo after=MetadataEngine.read(saved,name,p.mime,p.uri);
            assertTrue(after.animated);assertArrayEquals(payload(p.file,p.format),payload(saved,p.format));
            try{MetadataEngine.convertPng(p,edits(p),temp);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("动图"));}
        }
    }
    @Test public void bmpGifTiffRemainReadOnlyAndJsonWorks()throws Exception{
        for(String name:new String[]{"sample.bmp","sample.gif","sample.tiff","animated.gif"}){
            MetadataEngine.Photo p=read(name);assertFalse(p.writable);assertTrue(name,p.tags.length()>0);JSONObject report=MetadataEngine.report(p,edits(p));assertEquals(p.hash,report.getString("originalSha256"));assertEquals("上海市黄浦区 中文地址",report.getJSONObject("editedFields").getString("address"));try{MetadataEngine.writeCopy(p,edits(p),temp);fail();}catch(IOException expected){}
        }
    }
    @Test public void extensionDoesNotOverrideMagicAndBadGpsDoesNotWrite()throws Exception{
        MetadataEngine.Photo p=MetadataEngine.read(fixture("sample.png"),"fake.heic","image/heic","content://test");assertEquals("PNG",p.format);assertTrue(p.writable);
        Map<String,String> e=edits(p);e.put("latitude","NaN");try{MetadataEngine.writeCopy(p,e,temp);fail();}catch(IllegalArgumentException expected){}assertEquals(p.hash,MetadataEngine.hash(p.file));
    }
    @Test public void unrelatedEditsPreserveExistingGpsAltitudeAndTime()throws Exception{
        File original=fixture("sample.jpg");ExifInterface exif=new ExifInterface(original);exif.setLatLong(31.2304,121.4737);exif.setAltitude(125.5);exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP,"2026:10:13");exif.saveAttributes();
        MetadataEngine.Photo p=MetadataEngine.read(original,"sample.jpg","image/jpeg","content://test");Map<String,String> e=new LinkedHashMap<>(p.values);e.put("note","Only a note changed");
        File out=MetadataEngine.writeCopy(p,e,temp);ExifInterface saved=new ExifInterface(out);assertEquals(125.5,saved.getAltitude(-1),0.000001);assertEquals("2026:10:13",saved.getAttribute(ExifInterface.TAG_GPS_DATESTAMP));
    }
    // Compare compressed pixels, animation timing and animation frames, excluding metadata.
    private byte[] payload(File file,String format)throws Exception{
        byte[] data=Files.readAllBytes(file.toPath());ByteArrayOutputStream out=new ByteArrayOutputStream();
        if(format.equals("JPEG")){
            int pos=2;while(pos<data.length){while((data[pos]&255)==255)pos++;int marker=data[pos++]&255;if(marker==0xda){out.write(data,pos-2,data.length-pos+2);return out.toByteArray();}int length=((data[pos]&255)<<8)|(data[pos+1]&255);pos+=length;}throw new IOException("No JPEG pixels");
        }
        int pos=format.equals("PNG")?8:12;
        while(pos+8<=data.length){boolean png=format.equals("PNG");int n=png?((data[pos]&255)<<24)|((data[pos+1]&255)<<16)|((data[pos+2]&255)<<8)|(data[pos+3]&255):(data[pos+4]&255)|((data[pos+5]&255)<<8)|((data[pos+6]&255)<<16)|((data[pos+7]&255)<<24);String type=new String(data,pos+(png?4:0),4,"US-ASCII");int whole=8+n+(png?4:(n&1));
            if(png&&Arrays.asList("IHDR","IDAT","acTL","fcTL","fdAT","PLTE","tRNS").contains(type)||!png&&Arrays.asList("VP8 ","VP8L","ALPH","ANIM","ANMF").contains(type))out.write(data,pos,whole);pos+=whole;
        }return out.toByteArray();
    }
}
