package io.nrv.gameagent.input;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

public final class AccessibilityDiagnostics {

    private AccessibilityDiagnostics() {}

    public record Snapshot(
            boolean serviceInstalled,
            boolean accessibilityGloballyEnabled,
            boolean serviceEnabled,
            boolean serviceConnected,
            String manufacturer,
            String model,
            int sdk
    ) {
        public String summary() {
            return "Диагностика управления\n"
                    + mark(serviceInstalled) + " Служба найдена Android\n"
                    + mark(accessibilityGloballyEnabled) + " Accessibility включён в системе\n"
                    + mark(serviceEnabled) + " NRV Game Agent разрешён\n"
                    + mark(serviceConnected) + " Служба подключена\n"
                    + "Устройство: " + manufacturer + " " + model + " · Android API " + sdk;
        }

        public String nextStep() {
            if (!serviceInstalled) {
                return "Android не видит нашу Accessibility-службу. Нужна новая установка APK.";
            }
            if (!serviceEnabled) {
                return "Служба есть, но Android её не разрешил. На Xiaomi проверь 'Разрешить запрещённые/ограниченные настройки' в информации о приложении, затем снова открой Специальные возможности.";
            }
            if (!serviceConnected) {
                return "Разрешение сохранено, но служба ещё не подключилась. Вернись в приложение или перезапусти его один раз.";
            }
            return "Доступ к управлению работает. Можно включать управление и запускать захват экрана.";
        }

        private static String mark(boolean ok) {
            return ok ? "✓" : "✗";
        }
    }

    public static Snapshot read(Context context) {
        ComponentName component = new ComponentName(context, GameAccessibilityService.class);
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);

        boolean installed = false;
        if (manager != null) {
            List<AccessibilityServiceInfo> services = manager.getInstalledAccessibilityServiceList();
            if (services != null) {
                for (AccessibilityServiceInfo info : services) {
                    if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) continue;
                    String packageName = info.getResolveInfo().serviceInfo.packageName;
                    String className = info.getResolveInfo().serviceInfo.name;
                    ComponentName found = new ComponentName(packageName, className);
                    if (component.equals(found)) {
                        installed = true;
                        break;
                    }
                }
            }
        }

        boolean globalEnabled = false;
        try {
            globalEnabled = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
            ) == 1;
        } catch (Exception ignored) {
        }

        boolean enabled = false;
        try {
            String raw = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (!TextUtils.isEmpty(raw)) {
                TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
                splitter.setString(raw);
                while (splitter.hasNext()) {
                    ComponentName enabledComponent = ComponentName.unflattenFromString(splitter.next());
                    if (component.equals(enabledComponent)) {
                        enabled = true;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return new Snapshot(
                installed,
                globalEnabled,
                enabled,
                GameAccessibilityService.isConnected(),
                android.os.Build.MANUFACTURER,
                android.os.Build.MODEL,
                android.os.Build.VERSION.SDK_INT
        );
    }
}
