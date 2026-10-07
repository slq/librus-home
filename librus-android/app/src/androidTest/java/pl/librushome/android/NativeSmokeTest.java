package pl.librushome.android;

import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import static org.junit.Assert.*;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.chaquo.python.Python;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Run on a fresh emulator with synthetic demo and an isolated Keystore alias. */
@SuppressWarnings("deprecation")
public class NativeSmokeTest {

    @Test public void testDemoStartsWithRealBundledPythonLibraries() throws Exception {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MainActivity.class).putExtra("demo", true);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
        long deadline = System.currentTimeMillis() + 30000;
        boolean ready = false;
        while (System.currentTimeMillis() < deadline) {
            if (Python.isStarted()) {
                String raw = Python.getInstance().getModule("mobile_bridge").callAttr("get_service").callAttr("state_json").toString();
                if (new JSONObject(raw).optString("mode").equals("demo")) { ready = true; break; }
            }
            Thread.sleep(200);
        }
        assertTrue("Demo did not initialize", ready);
        Python.getInstance().getModule("lxml.etree");
        Python.getInstance().getModule("aiohttp");
        Python.getInstance().getModule("librus_shared.connector");
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        final boolean[] found = {false};
        scenario.onActivity(activity -> found[0] = containsText(activity.getWindow().getDecorView(), "Nieprzeczytane wiadomości"));
        assertTrue("Overview cards are missing", found[0]);
        }
    }

    @Test public void testKeystoreRoundtripUsesCiphertextAndClearsOnlyTestState() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SecureStore store = new SecureStore(context, "test-only-state.aes", "LibrusApp.test-only.v1");
        File file = new File(context.getNoBackupFilesDir(), "test-only-state.aes");
        try {
            store.write("{\"sample\":\"SYNTHETIC_SECRET_FOR_TEST\",\"unicode\":\"Łódź\"}");
            assertTrue(store.read().contains("Łódź"));
            assertFalse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1).contains("SYNTHETIC_SECRET_FOR_TEST"));
            byte[] corrupted = Files.readAllBytes(file.toPath());
            corrupted[corrupted.length - 1] ^= 1;
            Files.write(file.toPath(), corrupted);
            boolean rejected = false;
            try { store.read(); } catch (Exception expected) { rejected = true; }
            assertTrue("Corrupt ciphertext accepted", rejected);
            assertTrue(java.util.Arrays.equals(corrupted, Files.readAllBytes(file.toPath())));
        } finally { store.clear(); }
        assertFalse(file.exists());
    }

    private boolean containsText(View view, String expected) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(expected)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (containsText(group.getChildAt(i), expected)) return true;
        }
        return false;
    }
}
