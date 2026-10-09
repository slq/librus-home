package pl.librushome.android;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.widget.*;
import org.json.JSONObject;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Local status changes do not wait for Librus or share the alarm executor. */
public final class HomeworkCompletionUi {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    private final Activity activity;
    private final Supplier<JSONObject> current;
    private final Runnable changed;
    private final HomeworkStatusStore store;
    private Set<String> completed=Collections.emptySet();
    private final List<Binding> bindings=new ArrayList<>();
    private boolean ready,saving,refreshing;
    private long generation;
    private String context="",error="";
    private static final class Binding {
        final WeakReference<CheckBox> checkbox;final String key,id;
        Binding(CheckBox checkbox,String key,String id){this.checkbox=new WeakReference<>(checkbox);this.key=key;this.id=id;}
    }
    public HomeworkCompletionUi(Activity activity,Supplier<JSONObject> current,Runnable changed) {
        this.activity=activity;this.current=current;this.changed=changed;store=new HomeworkStatusStore(activity.getApplicationContext());
    }
    private String key(String id) {
        JSONObject state=current.get();boolean demo=state.optString("mode").equals("demo");
        try {return HomeworkStatusStore.key(demo?"demo":state.optString("profile"),demo,state.optString("year"),id);}
        catch(IllegalArgumentException missing){return null;}
    }
    public void contextChanged() {
        JSONObject state=current.get();String identity=state.optString("profile")+"/"+state.optString("year")+"/"+state.optString("mode").equals("demo");
        if(!context.equals(identity)){context=identity;reload();}
    }
    public void reload() {
        long request=++generation;ready=false;error="";notifyChanged();
        WORK.execute(()->{
            Set<String> values=null;try{values=store.completed();}catch(Exception ignored){}
            Set<String> loaded=values;
            activity.runOnUiThread(()->{
                if(activity.isDestroyed()||request!=generation)return;
                if(loaded!=null){completed=loaded;ready=true;error="";}
                else error="Nie można odczytać lokalnych statusów zadań. Zachowano zapis; oznaczanie jest wstrzymane.";
                notifyChanged();
            });
        });
    }
    public boolean ready(){return ready;}
    public boolean editable(){return ready&&!saving;}
    public boolean done(JSONObject item){String key=key(item.optString("id"));return ready&&key!=null&&completed.contains(key);}
    public String error(){return error;}
    public String progress(){return saving?"Zapisuję status zadania…":!ready&&error.isEmpty()?"Wczytuję lokalne statusy zadań…":"";}
    private void notifyChanged() {
        refreshing=true;
        for(Iterator<Binding> it=bindings.iterator();it.hasNext();) {
            Binding binding=it.next();CheckBox checkbox=binding.checkbox.get();if(checkbox==null){it.remove();continue;}
            boolean same=binding.key!=null&&binding.key.equals(key(binding.id));
            checkbox.setChecked(ready&&same&&completed.contains(binding.key));checkbox.setEnabled(editable()&&same);
        }
        refreshing=false;changed.run();
    }
    public void bind(LinearLayout parent,JSONObject item) {
        String id=item.optString("id"),key=key(id);CheckBox checkbox=new CheckBox(activity);
        checkbox.setText("Zrobione");checkbox.setTextColor(0xff183047);checkbox.setButtonTintList(ColorStateList.valueOf(ButtonStyles.TEAL));
        checkbox.setMinHeight(ButtonStyles.dp(activity,48));checkbox.setContentDescription("Zrobione: "+item.optString("title"));
        checkbox.setChecked(ready&&key!=null&&completed.contains(key));checkbox.setEnabled(editable()&&key!=null);
        bindings.add(new Binding(checkbox,key,id));parent.addView(checkbox);
        checkbox.setOnCheckedChangeListener((button,value)->{if(!refreshing)set(key,value);});
    }
    private void set(String key,boolean value) {
        if(!editable()||key==null)return;long expectedEpoch=HomeworkStatusStore.epoch(),request=generation;
        saving=true;notifyChanged();
        WORK.execute(()->{
            Set<String> saved=null;try {store.set(key,value,expectedEpoch);saved=store.completed();}catch(Exception ignored){}
            Set<String> result=saved;
            activity.runOnUiThread(()->{
                if(activity.isDestroyed())return;saving=false;
                if(request==generation) {
                    if(result!=null){completed=result;error="";ready=true;}
                    else {error="Nie zapisano statusu zadania. Zachowano poprzedni zapis. Spróbuj ponownie odczytać statusy.";ready=false;}
                }
                notifyChanged();
                if(result!=null)Toast.makeText(activity,value?"Zadanie oznaczone jako zrobione":"Zadanie przywrócone do zrobienia",Toast.LENGTH_SHORT).show();
            });
        });
    }
}
