package pl.librushome.android;
import android.app.Dialog;
import android.view.*;
import android.widget.*;
import static org.junit.Assert.*;

/** Drives real bottom controls and the More dialog, including secondary panels. Call on the UI thread. */
final class NavigationTestSupport {
    static Button button(View root,String label) {
        if(root instanceof Button && (((Button)root).getText().toString().equals(label)
                || (label.equals("Przypomnienia")&&((Button)root).getText().toString().startsWith("Przypomnienia ·"))))return (Button)root;
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++) {
            Button result=button(((ViewGroup)root).getChildAt(i),label);if(result!=null)return result;
        }
        return null;
    }
    static void open(MainActivity activity,String panel) {
        String label=panel.equals("Przegląd")?"Start":panel.equals("Zadania domowe")?"Zadania":panel;
        Button control=button(activity.getWindow().getDecorView(),label);
        if(control==null) {
            Button more=button(activity.getWindow().getDecorView(),"Więcej");assertNotNull(more);assertTrue(more.performClick());
            try {
                java.lang.reflect.Field field=MainActivity.class.getDeclaredField("moreDialog");field.setAccessible(true);
                Dialog dialog=(Dialog)field.get(activity);assertNotNull(dialog);assertTrue(dialog.isShowing());
                control=button(dialog.getWindow().getDecorView(),label);
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        }
        assertNotNull("Missing panel: "+panel,control);assertTrue(control.performClick());
    }
}
