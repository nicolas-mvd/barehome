package uy.nico.barehome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DogModeCoordinatorTest {
    private static int assertions, nextToken;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    static final class TestContext extends Context {
        final Map<String,String> preferences = new HashMap<>();
        public int checkSelfPermission(String permission) { return 0; }
        public void startActivity(Intent intent) {
            check(intent.target == MainActivity.class, "Unexpected navigation target"); homes++;
        }
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return new SharedPreferences() {
                public String getString(String key,String fallback) { return preferences.getOrDefault(name+key, fallback); }
                public Editor edit() { return new Editor() {
                    String key,value;
                    public Editor putString(String key,String value) { this.key=name+key;this.value=value;return this; }
                    public boolean commit() { preferences.put(key,value); return true; }
                }; }
            };
        }
    }
    private static TestContext fixture() {
        TestContext context = new TestContext();
        context.resolver.secure.put("sleep_timeout", "86400000");
        context.resolver.secure.put("attentive_timeout", "14100000");
        context.resolver.global.put("no_signal_auto_power_off", "3");
        android.os.Build.MODEL="CSV4K";android.os.Build.DEVICE="zhongshan";
        android.os.Build.PRODUCT="KTC_MOTOROLA_4K_SA_21V2";android.os.Build.VERSION.SDK_INT=30;
        NsNowDogService.reset(); DogModeAccessibility.consent=true; DogModeAccessibility.commit=true;
        SystemClock.now=0; Handler.clear();
        return context;
    }
    private static String request(Context context,String action,List<String> finalReplies) throws Exception {
        String token=String.format(java.util.Locale.ROOT,"%064x",++nextToken);
        DogModeWire.Request wire=DogModeWire.parse("barehome://dog/"+action
                +"?reply=192.168.1.8&port=12345&token="+token);
        return DogModeCoordinator.request(context,wire,SystemClock.elapsedRealtime()+7000,
                finalReplies==null?null:finalReplies::add);
    }
    private static void original(TestContext context) {
        check("86400000".equals(context.resolver.secure.get("sleep_timeout"))
                && "14100000".equals(context.resolver.secure.get("attentive_timeout"))
                && "3".equals(context.resolver.global.get("no_signal_auto_power_off")), "Original values not restored");
    }
    private static List<String> replies() { return Collections.synchronizedList(new ArrayList<String>()); }

    public static void main(String[] args) throws Exception {
        TestContext context=fixture(); List<String> first=replies(), second=replies();
        check("starting".equals(request(context,"start",first)),"Start did not return promptly pending");
        String generation=NsNowDogService.generation;
        check(DogModeCoordinator.permitsHelper(context,generation,false),"Pending generation not authorized");
        check(!DogModeCoordinator.permitsHelper(context,generation,true),"Unverified generation authorized prompt handler");
        check("starting".equals(request(context,"start",second)) && NsNowDogService.begins==1,"Second client restarted selector");
        NsNowDogService.callback.complete(true);
        check("active".equals(request(context,"state",null)),"Verified completion not active");
        check(first.equals(java.util.Arrays.asList("active")) && second.equals(java.util.Arrays.asList("active")),"Final callback missing");
        check(DogModeCoordinator.permitsHelper(context,generation,true),"Verified active generation not authorized");
        NsNowDogService.monitoring=false;
        check("restore_required".equals(request(context,"state",null)),"Lost helper still reported active");
        check("restore_required".equals(request(context,"start",null)),"Duplicate start bypassed strict helper check");
        NsNowDogService.monitoring=true;
        NsNowDogService.disableFailure=true;
        check("restore_required".equals(request(context,"stop",null)),"Helper removal failure reported off");
        original(context);
        check(!new DogModeStore(context).read().isEmpty(),"Failed removal discarded snapshot");
        NsNowDogService.disableFailure=false;
        check("off".equals(request(context,"stop",null)) && !NsNowDogService.enabled,"Removal recovery failed");

        context=fixture(); first=replies();
        check("starting".equals(request(context,"start",first)),"Pending test start failed");
        NsNowDogService.Completion old=NsNowDogService.callback;
        generation=NsNowDogService.generation;
        check("off".equals(request(context,"stop",null)),"Stop during startup failed");
        check(!DogModeCoordinator.permitsHelper(context,generation,false),"Stopped selector still authorized");
        check(first.equals(java.util.Arrays.asList("off")),"Stopped startup endpoint left waiting");
        old.complete(true);
        check("off".equals(request(context,"state",null)),"Late completion reactivated stopped mode"); original(context);
        check("starting".equals(request(context,"start",null)),"New start failed");
        String newGeneration=NsNowDogService.generation;
        check(!generation.equals(newGeneration),"New start reused generation");
        old.complete(true);
        check("starting".equals(request(context,"state",null)),"Old callback changed a new transaction");
        NsNowDogService.callback.complete(false);
        check("off".equals(request(context,"state",null)),"Failed selector was not rolled back"); original(context);

        context=fixture(); NsNowDogService.schedule=false;
        check("error".equals(request(context,"start",null)),"Enable/start failure not reported"); original(context);
        check(!NsNowDogService.enabled && new DogModeStore(context).read().isEmpty(),"Failed start left helper/journal");
        context=fixture(); first=replies();
        check("starting".equals(request(context,"start",first)),"Timeout setup failed");
        SystemClock.now=30_001; Handler.runDelayed();
        check("off".equals(request(context,"state",null)),"Startup watchdog did not roll back"); original(context);
        check(first.equals(java.util.Arrays.asList("error")),"Timeout final reply wrong/missing");

        context=fixture(); context.resolver.expireOnRead=true;
        check("error".equals(request(context,"start",null)),"Expired preparation did not report error");
        request(context,"state",null);
        check(NsNowDogService.begins==0,"Selector launched after receiver deadline"); original(context);

        context=fixture();
        request(context,"start",null); NsNowDogService.callback.complete(true); request(context,"state",null);
        final boolean[] revoked={false};
        DogModeCoordinator.revokeConsent(context, result -> revoked[0]=result);
        request(context,"state",null);
        check(revoked[0] && !DogModeAccessibility.consent && !NsNowDogService.enabled,"Revocation did not disable and restore"); original(context);
        context=fixture(); NsNowDogService.enabled=true; NsNowDogService.disableFailure=true;
        check("restore_required".equals(request(context,"state",null)),"Orphan enabled helper reported off");
        check("restore_required".equals(request(context,"stop",null)),"Orphan helper removal failure reported off");
        check("restore_required".equals(request(context,"state",null)),"Orphan cleanup failure disappeared from state");
        NsNowDogService.disableFailure=false;
        check("off".equals(request(context,"stop",null)),"Orphan cleanup could not recover");

        for(String code:new String[]{"nsnow_access","nsnow_menu","nsnow_channel","nsnow_player","nsnow_debug","nsnow_target","nsnow_input"}){
            context=fixture();first=replies();request(context,"start",first);
            NsNowDogService.failureCode=code;NsNowDogService.callback.complete(false);
            check("off".equals(request(context,"state",null)),"Diagnostic was persisted as TV state");
            check(first.equals(java.util.Arrays.asList(code)),"Verified rollback diagnostic not delivered");
            check(!NsNowDogService.enabled&&new DogModeStore(context).read().isEmpty(),"Diagnostic claimed cleanup without confirmation");
            original(context);
        }
        context=fixture();first=replies();request(context,"start",first);
        NsNowDogService.failureCode="nsnow_channel";NsNowDogService.disableFailure=true;
        NsNowDogService.callback.complete(false);
        check("restore_required".equals(request(context,"state",null)),"Failed diagnostic cleanup lost recovery state");
        check(first.equals(java.util.Arrays.asList("restore_required")),"Diagnostic falsely claimed restoration when helper removal failed");
        NsNowDogService.disableFailure=false;request(context,"stop",null);
        context=fixture();first=replies();request(context,"start",first);
        NsNowDogService.failureCode="private arbitrary content";NsNowDogService.callback.complete(false);
        request(context,"state",null);
        check(first.equals(java.util.Arrays.asList("error")),"Arbitrary helper diagnostic escaped coarse allowlist");
        System.out.println("DogModeCoordinator: "+assertions+" behavioral assertions passed");
    }
}
