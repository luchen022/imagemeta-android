package com.jaden.imagemeta;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ActivitySmokeTest {
    @Test public void opensAndClosesWithoutSelectedPhoto() throws Exception {
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity activity=controller.get();assertNotNull(activity.findViewById(android.R.id.content));
        View decor=activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY));decor.layout(0,0,1080,1920);
        Bitmap image=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888);decor.draw(new Canvas(image));
        File output=new File("build/ui-preview.png");output.getParentFile().mkdirs();try(FileOutputStream stream=new FileOutputStream(output)){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,stream));}image.recycle();
        controller.pause().stop().destroy();
    }
}
