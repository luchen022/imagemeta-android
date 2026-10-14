package com.jaden.imagemeta;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.exifinterface.media.ExifInterface;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE,sdk=28)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ConversionTest {
    @Test public void conversionKeepsFullSizeTransparencyAndWritesEdits()throws Exception{
        File dir=Files.createTempDirectory("conversion-test").toFile();
        try{for(String name:new String[]{"sample.bmp","sample.png","sample.jpg"}){
            File input=new File(dir,name);try(InputStream in=getClass().getResourceAsStream("/fixtures/"+name);OutputStream out=new FileOutputStream(input)){MetadataEngine.copy(in,out);}
            MetadataEngine.Photo p=MetadataEngine.read(input,name,null,"content://test");Map<String,String> values=new LinkedHashMap<>(p.values);values.put("latitude","31.2304");values.put("longitude","121.4737");values.put("address","完整尺寸测试");
            File result=MetadataEngine.convertPng(p,values,dir);Bitmap bitmap=BitmapFactory.decodeFile(result.getPath());assertNotNull(bitmap);assertEquals(32,bitmap.getWidth());assertEquals(24,bitmap.getHeight());if(name.endsWith("png"))assertEquals(128,android.graphics.Color.alpha(bitmap.getPixel(0,0)));bitmap.recycle();assertEquals("完整尺寸测试",MetadataEngine.read(result,"out.png",null,"content://test").values.get("address"));assertEquals(p.hash,MetadataEngine.hash(input));
        }}finally{for(File f:Objects.requireNonNull(dir.listFiles()))f.delete();dir.delete();}
    }
    @Test public void convertedPixelsRespectExifOrientation()throws Exception{
        File dir=Files.createTempDirectory("orientation-test").toFile();
        try{File input=new File(dir,"rotated.jpg");try(InputStream in=getClass().getResourceAsStream("/fixtures/sample.jpg");OutputStream out=new FileOutputStream(input)){MetadataEngine.copy(in,out);}ExifInterface e=new ExifInterface(input);e.setAttribute(ExifInterface.TAG_ORIENTATION,"6");e.saveAttributes();MetadataEngine.Photo p=MetadataEngine.read(input,"rotated.jpg",null,"content://test");File out=MetadataEngine.convertPng(p,p.values,dir);Bitmap b=BitmapFactory.decodeFile(out.getPath());assertEquals(24,b.getWidth());assertEquals(32,b.getHeight());b.recycle();}
        finally{for(File f:Objects.requireNonNull(dir.listFiles()))f.delete();dir.delete();}
    }
}
