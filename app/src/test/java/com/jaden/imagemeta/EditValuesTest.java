package com.jaden.imagemeta;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class EditValuesTest {
    @Test public void validCoordinates(){assertArrayEquals(new double[]{31.2304,121.4737},EditValues.coordinates("31.2304","121.4737"),0);assertNull(EditValues.coordinates(" ",""));assertArrayEquals(new double[]{-90,180},EditValues.coordinates("-90","180"),0);}
    @Test public void rejectPartial(){reject("10","");reject("NaN","1");reject("91","120");reject("0","Infinity");reject("0","181");}
    private void reject(String a,String b){try{EditValues.coordinates(a,b);fail();}catch(IllegalArgumentException expected){}}
    @Test public void validateCalendarAndUrl(){Map<String,String> m=new HashMap<>();m.put("DateTimeOriginal","2024:02:29 23:59:59");m.put("sourceUrl","https://example.com/photo");EditValues.validate(m);m.put("DateTimeOriginal","2026:02:29 12:00:00");try{EditValues.validate(m);fail();}catch(IllegalArgumentException expected){}m.put("DateTimeOriginal","");m.put("sourceUrl","javascript:alert(1)");try{EditValues.validate(m);fail();}catch(IllegalArgumentException expected){}}
}
