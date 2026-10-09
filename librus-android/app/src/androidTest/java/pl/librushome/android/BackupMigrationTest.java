package pl.librushome.android;
import android.content.Context;
import org.json.*;
import org.junit.Test;
import java.io.*;
import static org.junit.Assert.*;

/** Host preserves the synthetic JSON, uninstalls on the blank emulator, reinstalls and returns the file. */
public class BackupMigrationTest {
    @Test public void seed()throws Exception {
        BackupTestSupport.blank();Context c=BackupTestSupport.context();BackupDocument doc=BackupTestSupport.seed();
        try(FileOutputStream out=new FileOutputStream(new File(c.getExternalFilesDir(null),"backup-migration.json"))){out.write(doc.bytes());}
        assertTrue(new SecureStore(c).read().contains("SYNTHETIC_PASSWORD_NEVER_EXPORT"));assertFalse(new String(doc.bytes(),java.nio.charset.StandardCharsets.UTF_8).contains("SYNTHETIC_PASSWORD_NEVER_EXPORT"));
    }
    @Test public void prepare()throws Exception {
        BackupTestSupport.blank();Context c=BackupTestSupport.context();assertTrue(new SecureStore(c).read().isEmpty());
        java.security.KeyStore keys=java.security.KeyStore.getInstance("AndroidKeyStore");keys.load(null);assertFalse(keys.containsAlias("LibrusApp.local.v1"));
        assertTrue(c.getExternalFilesDir(null).isDirectory());
    }
    @Test public void verify()throws Exception {
        BackupTestSupport.blank();Context c=BackupTestSupport.context();JSONObject before=BackupStore.before(c);assertTrue(new SecureStore(c).read().isEmpty());
        try(FileInputStream in=new FileInputStream(new File(c.getExternalFilesDir(null),"backup-migration.json"))){BackupDocument doc=BackupDocument.read(in);BackupTestSupport.school();
            PythonValidation.validate(doc);BackupStore.apply(c,doc);BackupTestSupport.check(doc);
            com.chaquo.python.PyObject restored=com.chaquo.python.Python.getInstance().getModule("mobile_bridge").get("MobileService").call();restored.callAttr("restore_json",new SecureStore(c).read());assertFalse(new JSONObject(restored.callAttr("state_json").toString()).getBoolean("remembered"));
        }finally{BackupTestSupport.clean(before);new File(c.getExternalFilesDir(null),"backup-migration.json").delete();}
    }
    static class PythonValidation {static void validate(BackupDocument doc)throws Exception {doc.root.put("school",new JSONObject(com.chaquo.python.Python.getInstance().getModule("mobile_bridge").get("MobileService").call().callAttr("validate_portable_json",doc.school()).toString()));}}
}
