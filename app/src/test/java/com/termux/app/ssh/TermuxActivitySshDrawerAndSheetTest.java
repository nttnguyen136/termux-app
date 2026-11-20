package com.termux.app.ssh;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.app.terminal.TerminalActionsBottomSheet;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.shell.TermuxShellManager;
import com.termux.shared.termux.theme.TermuxAppearancePreferences;
import com.termux.shared.termux.theme.TermuxColorPalette;
import com.termux.shared.termux.theme.TermuxFontManager;
import com.termux.shared.termux.theme.TermuxThemeManager;
import com.termux.shared.termux.theme.TermuxThemePresets;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class TermuxActivitySshDrawerAndSheetTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    private Context appContext;
    private File prefixDir;
    private File homeDir;
    private File tmpDir;
    private File storageDir;
    private SshProfileRepository repository;
    private List<SshSessionLaunchRequest> launchedRequests;
    private AtomicLong clockMillis;

    @Before
    public void setUp() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        appContext = app;
        resetGlobalAppearanceState(appContext);
        TermuxThemeManager.setSelectedThemeId(appContext, TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);

        TermuxShellManager manager = TermuxShellManager.getShellManager();
        if (manager != null) {
            manager.mTermuxSessions.clear();
            manager.mTermuxTasks.clear();
        }
        TermuxService termuxService = Robolectric.buildService(TermuxService.class).create().get();
        termuxService.createTermuxSession("/system/bin/sh", null, null, "/", true, "TestShell");
        Shadows.shadowOf(app).setComponentNameAndServiceForBindService(
            new ComponentName(app, TermuxService.class),
            termuxService.onBind(new Intent(app, TermuxService.class))
        );

        prefixDir = tempFolder.newFolder("prefix");
        homeDir = tempFolder.newFolder("home");
        tmpDir = tempFolder.newFolder("tmp");
        storageDir = tempFolder.newFolder("storage");
        repository = new SshProfileRepository(storageDir, AesGcmSshSecretCipher.withRandomKey());
        launchedRequests = new ArrayList<>();
        clockMillis = new AtomicLong(1_800_000_000_000L);

        TermuxActivity.setSshDrawerDependencyFactoryForTests(activity ->
            new SshDrawerCoordinator.Dependencies(
                activity,
                repository,
                prefixDir,
                homeDir,
                tmpDir,
                request -> {
                    launchedRequests.add(request);
                    return true;
                },
                clockMillis::get
            )
        );
    }

    @After
    public void tearDown() {
        TermuxActivity.setSshDrawerDependencyFactoryForTests(null);
        TermuxShellManager manager = TermuxShellManager.getShellManager();
        if (manager != null) {
            manager.mTermuxSessions.clear();
            manager.mTermuxTasks.clear();
        }
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(appContext);
        if (prefs != null) {
            prefs.setCurrentSession(null);
        }
        resetGlobalAppearanceState(appContext);
    }

    private static void resetGlobalAppearanceState(Context context) {
        if (TermuxAppearancePreferences.getPreferences(context) != null) {
            TermuxAppearancePreferences.getPreferences(context).edit().clear().commit();
        }
        com.termux.terminal.TerminalColors.COLOR_SCHEME.updateWith(new java.util.Properties());
        com.termux.shared.theme.NightMode.setAppNightMode(com.termux.shared.theme.NightMode.TRUE.getName());
    }

    @Test
    public void terminalActionsBottomSheet_showsThemedSshHostsRowAndLaunchesManager() {
        TermuxThemeManager.setSelectedThemeId(appContext, TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
        TermuxColorPalette latte = TermuxThemePresets.getBuiltInById(
            TermuxColorPalette.THEME_CATPPUCCIN_LATTE);

        try (ActivityController<TermuxActivity> controller =
                 Robolectric.buildActivity(TermuxActivity.class)) {
            TermuxActivity activity = controller.create().start().resume().get();

            activity.showTerminalActionsBottomSheet();
            BottomSheetDialog dialog = activity.getTerminalActionsBottomSheet();
            assertNotNull(dialog);
            assertTrue(dialog.isShowing());

            View root = dialog.findViewById(R.id.terminal_actions_sheet_root);
            assertNotNull(root);
            assertTrue(root.getBackground() instanceof GradientDrawable);

            LinearLayout list = dialog.findViewById(R.id.terminal_actions_list);
            assertNotNull(list);
            View sshRow = list.findViewWithTag(TerminalActionsBottomSheet.ActionIds.SSH_HOSTS);
            assertNotNull(sshRow);

            TextView label = sshRow.findViewById(R.id.action_label);
            ImageView icon = sshRow.findViewById(R.id.action_icon);
            assertNotNull(label);
            assertNotNull(icon);
            assertEquals(activity.getString(R.string.action_open_ssh_hosts), label.getText().toString());
            assertEquals(latte.onSurface, label.getCurrentTextColor());
            assertNotNull(icon.getDrawable());
            assertEquals(latte.onSurfaceVariant, icon.getImageTintList().getDefaultColor());

            sshRow.performClick();
            assertFalse(dialog.isShowing());

            Intent launched = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(launched);
            assertEquals(SshHostsActivity.class.getName(), launched.getComponent().getClassName());
        }
    }

    @Test
    public void leftDrawer_showsSshHostsEmptyPromptAndManageActionOpensSshHostsActivity() {
        try (ActivityController<TermuxActivity> controller =
                 Robolectric.buildActivity(TermuxActivity.class)) {
            TermuxActivity activity = controller.create().start().resume().get();

            LinearLayout leftDrawer = activity.findViewById(R.id.left_drawer);
            ListView sessionsList = activity.findViewById(R.id.terminal_sessions_list);
            View sshSection = activity.findViewById(R.id.drawer_ssh_section);
            View footerDivider = activity.findViewById(R.id.drawer_footer_divider);
            assertNotNull(leftDrawer);
            assertNotNull(sessionsList);
            assertNotNull(sshSection);
            assertNotNull(footerDivider);

            LinearLayout.LayoutParams sessionsLp = (LinearLayout.LayoutParams) sessionsList.getLayoutParams();
            assertEquals(1.0f, sessionsLp.weight, 0.001f);

            int sessionsIdx = leftDrawer.indexOfChild(sessionsList);
            int sshIdx = leftDrawer.indexOfChild(sshSection);
            int footerDividerIdx = leftDrawer.indexOfChild(footerDivider);
            assertTrue(sessionsIdx >= 0);
            assertTrue(sshIdx > sessionsIdx);
            assertTrue(footerDividerIdx > sshIdx);

            assertEquals(View.VISIBLE, sshSection.getVisibility());
            TextView sectionTitle = activity.findViewById(R.id.drawer_ssh_section_title);
            TextView manageButton = activity.findViewById(R.id.drawer_ssh_manage_button);
            LinearLayout hostsList = activity.findViewById(R.id.drawer_ssh_hosts_list);
            TextView emptyState = activity.findViewById(R.id.drawer_ssh_empty_state);

            assertNotNull(sectionTitle);
            assertNotNull(manageButton);
            assertNotNull(hostsList);
            assertNotNull(emptyState);

            assertEquals(activity.getString(R.string.drawer_ssh_hosts_title), sectionTitle.getText().toString());
            assertEquals(View.VISIBLE, manageButton.getVisibility());
            assertEquals(activity.getString(R.string.drawer_ssh_hosts_manage), manageButton.getText().toString());
            assertEquals(View.GONE, hostsList.getVisibility());
            assertEquals(0, hostsList.getChildCount());
            assertEquals(View.VISIBLE, emptyState.getVisibility());
            assertEquals(activity.getString(R.string.drawer_ssh_hosts_empty), emptyState.getText().toString());

            DrawerLayout drawer = activity.getDrawer();
            drawer.openDrawer(GravityCompat.START);
            assertTrue(drawer.isDrawerOpen(GravityCompat.START));

            manageButton.performClick();
            assertFalse(drawer.isDrawerOpen(GravityCompat.START));

            Intent launched = Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(launched);
            assertEquals(SshHostsActivity.class.getName(), launched.getComponent().getClassName());
        }
    }

    @Test
    public void leftDrawer_ordersPinnedThenRecentlyUsedCappedAtFourAndRefreshesOnResume()
            throws Exception {
        SshProfile unpinnedOlder = SshProfile.builder()
            .id("id-older")
            .displayName("Unpinned Older")
            .host("older.internal")
            .username("ops")
            .authType(SshAuthType.SSH_KEY)
            .pinned(false)
            .lastUsedAtMillis(1000L)
            .build();
        SshProfile pinnedBox = SshProfile.builder()
            .id("id-pinned-box")
            .displayName("Pinned Box")
            .host("box.internal")
            .username("alice")
            .authType(SshAuthType.SSH_KEY)
            .pinned(true)
            .lastUsedAtMillis(500L)
            .build();
        SshProfile recentPassword = SshProfile.builder()
            .id("id-recent-pw")
            .displayName("Recent Password")
            .host("pw.internal")
            .port(2222)
            .username("deploy")
            .authType(SshAuthType.PASSWORD)
            .pinned(false)
            .lastUsedAtMillis(9000L)
            .build();
        SshProfile recentKey = SshProfile.builder()
            .id("id-recent-key")
            .displayName("Recent Key")
            .host("key.internal")
            .username("bob")
            .authType(SshAuthType.SSH_KEY)
            .pinned(false)
            .lastUsedAtMillis(8000L)
            .build();
        SshProfile pinnedNewer = SshProfile.builder()
            .id("id-pinned-newer")
            .displayName("Pinned Newer")
            .host("newer.internal")
            .username("root")
            .authType(SshAuthType.PASSWORD)
            .pinned(true)
            .lastUsedAtMillis(2000L)
            .build();

        repository.saveProfile(unpinnedOlder);
        repository.saveProfile(pinnedBox);
        repository.saveProfile(recentPassword);
        repository.saveProfile(recentKey);
        repository.saveProfile(pinnedNewer);

        try (ActivityController<TermuxActivity> controller =
                 Robolectric.buildActivity(TermuxActivity.class)) {
            TermuxActivity activity = controller.create().start().resume().get();

            LinearLayout hostsList = activity.findViewById(R.id.drawer_ssh_hosts_list);
            View emptyState = activity.findViewById(R.id.drawer_ssh_empty_state);
            assertEquals(View.VISIBLE, hostsList.getVisibility());
            assertEquals(View.GONE, emptyState.getVisibility());
            assertEquals(4, hostsList.getChildCount());

            View row0 = hostsList.getChildAt(0);
            assertEquals("Pinned Newer", ((TextView) row0.findViewById(R.id.drawer_ssh_host_title)).getText().toString());
            assertEquals("root@newer.internal", ((TextView) row0.findViewById(R.id.drawer_ssh_host_subtitle)).getText().toString());
            assertEquals(View.VISIBLE, row0.findViewById(R.id.drawer_ssh_host_pin_badge).getVisibility());
            assertEquals(activity.getString(R.string.ssh_auth_type_password),
                ((TextView) row0.findViewById(R.id.drawer_ssh_host_auth_badge)).getText().toString());

            View row1 = hostsList.getChildAt(1);
            assertEquals("Pinned Box", ((TextView) row1.findViewById(R.id.drawer_ssh_host_title)).getText().toString());
            assertEquals(View.VISIBLE, row1.findViewById(R.id.drawer_ssh_host_pin_badge).getVisibility());
            assertEquals(activity.getString(R.string.ssh_auth_type_key),
                ((TextView) row1.findViewById(R.id.drawer_ssh_host_auth_badge)).getText().toString());

            View row2 = hostsList.getChildAt(2);
            assertEquals("Recent Password", ((TextView) row2.findViewById(R.id.drawer_ssh_host_title)).getText().toString());
            assertEquals("deploy@pw.internal:2222", ((TextView) row2.findViewById(R.id.drawer_ssh_host_subtitle)).getText().toString());
            assertEquals(View.GONE, row2.findViewById(R.id.drawer_ssh_host_pin_badge).getVisibility());

            View row3 = hostsList.getChildAt(3);
            assertEquals("Recent Key", ((TextView) row3.findViewById(R.id.drawer_ssh_host_title)).getText().toString());
            assertEquals("bob@key.internal", ((TextView) row3.findViewById(R.id.drawer_ssh_host_subtitle)).getText().toString());
            assertEquals(View.GONE, row3.findViewById(R.id.drawer_ssh_host_pin_badge).getVisibility());

            // Simulate user navigating away to SshHostsActivity, adding a newer pinned host, and returning
            controller.pause().stop();
            SshProfile newlyPinned = SshProfile.builder()
                .id("id-newly-pinned")
                .displayName("Newly Pinned")
                .host("fresh.internal")
                .username("admin")
                .authType(SshAuthType.SSH_KEY)
                .pinned(true)
                .lastUsedAtMillis(99_000L)
                .build();
            repository.saveProfile(newlyPinned);

            controller.start().resume();
            assertEquals(4, hostsList.getChildCount());
            assertEquals("Newly Pinned",
                ((TextView) hostsList.getChildAt(0).findViewById(R.id.drawer_ssh_host_title)).getText().toString());

            // Deleting all profiles while stopped restores empty state on resume
            controller.pause().stop();
            for (SshProfile p : repository.getAllSorted()) {
                repository.deleteProfile(p.id);
            }
            controller.start().resume();
            assertEquals(View.GONE, hostsList.getVisibility());
            assertEquals(0, hostsList.getChildCount());
            assertEquals(View.VISIBLE, emptyState.getVisibility());
        }
    }

    @Test
    public void tappingDrawerHost_closesDrawerLaunchesSshSessionWithAskPassAndRefreshesOrder()
            throws Exception {
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File sshBinary = new File(binDir, "ssh");
        assertTrue(sshBinary.createNewFile());
        assertTrue(sshBinary.setExecutable(true, true));

        SshProfile newerBox = SshProfile.builder()
            .id("id-newer")
            .displayName("Newer Box")
            .host("newer.internal")
            .username("ops")
            .authType(SshAuthType.SSH_KEY)
            .pinned(false)
            .lastUsedAtMillis(5000L)
            .build();
        SshProfile olderBox = SshProfile.builder()
            .id("id-older")
            .displayName("Older Password Box")
            .host("older.internal")
            .port(2222)
            .username("deploy")
            .authType(SshAuthType.PASSWORD)
            .pinned(false)
            .lastUsedAtMillis(1000L)
            .build();
        repository.saveProfile(newerBox);
        repository.saveProfileWithSecret(olderBox, "drawer-secret-pw");

        try (ActivityController<TermuxActivity> controller =
                 Robolectric.buildActivity(TermuxActivity.class)) {
            TermuxActivity activity = controller.create().start().resume().get();

            LinearLayout hostsList = activity.findViewById(R.id.drawer_ssh_hosts_list);
            assertEquals(2, hostsList.getChildCount());
            assertEquals("Newer Box",
                ((TextView) hostsList.getChildAt(0).findViewById(R.id.drawer_ssh_host_title)).getText().toString());
            assertEquals("Older Password Box",
                ((TextView) hostsList.getChildAt(1).findViewById(R.id.drawer_ssh_host_title)).getText().toString());

            DrawerLayout drawer = activity.getDrawer();
            drawer.openDrawer(GravityCompat.START);
            assertTrue(drawer.isDrawerOpen(GravityCompat.START));

            clockMillis.set(1_900_000_000_000L);
            hostsList.getChildAt(1).performClick();

            assertFalse(drawer.isDrawerOpen(GravityCompat.START));
            assertEquals(1, launchedRequests.size());
            SshSessionLaunchRequest req = launchedRequests.get(0);
            assertEquals(sshBinary.getAbsolutePath(), req.executablePath);
            assertEquals("Older Password Box", req.sessionTitle);
            assertEquals(homeDir.getAbsolutePath(), req.workingDirectory);
            assertTrue(req.arguments.contains("-p"));
            assertTrue(req.arguments.contains("2222"));
            assertTrue(req.arguments.contains("deploy@older.internal"));
            assertNotNull(req.additionalEnvironment.get(SshAskPassManager.ENV_SSH_ASKPASS));
            assertEquals("force", req.additionalEnvironment.get(SshAskPassManager.ENV_SSH_ASKPASS_REQUIRE));
            assertEquals(":0", req.additionalEnvironment.get(SshAskPassManager.ENV_DISPLAY));

            assertEquals(1_900_000_000_000L, repository.getById(olderBox.id).lastUsedAtMillis);
            assertEquals("Older Password Box",
                ((TextView) hostsList.getChildAt(0).findViewById(R.id.drawer_ssh_host_title)).getText().toString());
        }
    }

    @Test
    public void tappingDrawerHostWhenOpenSshMissing_closesDrawerPromptsDialogAndLaunchesOnConfirm()
            throws Exception {
        SshProfile otherBox = SshProfile.builder()
            .id("id-other")
            .displayName("Other Box")
            .host("other.internal")
            .username("alice")
            .pinned(false)
            .lastUsedAtMillis(5000L)
            .build();
        SshProfile edgeBox = SshProfile.builder()
            .id("id-edge")
            .displayName("Edge Box")
            .host("edge.internal")
            .port(2202)
            .username("ubuntu")
            .authType(SshAuthType.PASSWORD)
            .pinned(false)
            .lastUsedAtMillis(1000L)
            .build();
        repository.saveProfile(otherBox);
        repository.saveProfileWithSecret(edgeBox, "edge-pw");

        try (ActivityController<TermuxActivity> controller =
                 Robolectric.buildActivity(TermuxActivity.class)) {
            TermuxActivity activity = controller.create().start().resume().get();

            LinearLayout hostsList = activity.findViewById(R.id.drawer_ssh_hosts_list);
            assertEquals("Other Box",
                ((TextView) hostsList.getChildAt(0).findViewById(R.id.drawer_ssh_host_title)).getText().toString());
            assertEquals("Edge Box",
                ((TextView) hostsList.getChildAt(1).findViewById(R.id.drawer_ssh_host_title)).getText().toString());

            DrawerLayout drawer = activity.getDrawer();
            drawer.openDrawer(GravityCompat.START);
            assertTrue(drawer.isDrawerOpen(GravityCompat.START));

            clockMillis.set(1_950_000_000_000L);
            hostsList.getChildAt(1).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertFalse(drawer.isDrawerOpen(GravityCompat.START));
            assertTrue(launchedRequests.isEmpty());

            android.app.Dialog latest = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(latest instanceof androidx.appcompat.app.AlertDialog);
            androidx.appcompat.app.AlertDialog installDialog = (androidx.appcompat.app.AlertDialog) latest;
            assertTrue(installDialog.isShowing());
            assertEquals(
                activity.getString(R.string.ssh_missing_openssh_title),
                ((TextView) installDialog.findViewById(R.id.ssh_install_dialog_title)).getText().toString()
            );

            installDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();

            assertFalse(installDialog.isShowing());
            assertEquals(1, launchedRequests.size());
            SshSessionLaunchRequest req = launchedRequests.get(0);
            assertTrue(req.arguments.get(1).startsWith("pkg install -y openssh && exec "));
            assertTrue(req.arguments.get(1).contains("ubuntu@edge.internal"));
            assertNotNull(req.additionalEnvironment.get(SshAskPassManager.ENV_SSH_ASKPASS));
            assertEquals(1_950_000_000_000L, repository.getById(edgeBox.id).lastUsedAtMillis);
            assertEquals("Edge Box",
                ((TextView) hostsList.getChildAt(0).findViewById(R.id.drawer_ssh_host_title)).getText().toString());
        }
    }

    @Test
    public void liveThemeSwitch_updatesDrawerSshSectionAndCardsInPlaceWithoutRecreatingActivity()
            throws Exception {
        SshProfile pinnedKey = SshProfile.builder()
            .id("id-theme-pinned")
            .displayName("Pinned Theme Host")
            .host("pinned.internal")
            .username("ops")
            .authType(SshAuthType.SSH_KEY)
            .pinned(true)
            .lastUsedAtMillis(2000L)
            .build();
        SshProfile recentPw = SshProfile.builder()
            .id("id-theme-pw")
            .displayName("Recent Theme Host")
            .host("pw.internal")
            .username("deploy")
            .authType(SshAuthType.PASSWORD)
            .pinned(false)
            .lastUsedAtMillis(1000L)
            .build();
        repository.saveProfile(pinnedKey);
        repository.saveProfile(recentPw);

        try (ActivityController<TermuxActivity> controller =
                 Robolectric.buildActivity(TermuxActivity.class)) {
            TermuxActivity activity = controller.create().start().resume().get();

            TermuxColorPalette mocha = TermuxThemePresets.getBuiltInById(
                TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);
            View divider = activity.findViewById(R.id.drawer_ssh_divider);
            TextView sectionTitle = activity.findViewById(R.id.drawer_ssh_section_title);
            TextView manageButton = activity.findViewById(R.id.drawer_ssh_manage_button);
            LinearLayout hostsList = activity.findViewById(R.id.drawer_ssh_hosts_list);
            TextView emptyState = activity.findViewById(R.id.drawer_ssh_empty_state);

            assertEquals(ColorUtils.setAlphaComponent(mocha.outline, 80),
                ((ColorDrawable) divider.getBackground()).getColor());
            assertEquals(mocha.onSurfaceVariant, sectionTitle.getCurrentTextColor());
            assertEquals(mocha.accent, manageButton.getCurrentTextColor());

            View row0 = hostsList.getChildAt(0);
            assertTrue(row0.getBackground() instanceof GradientDrawable);
            GradientDrawable row0Bg = (GradientDrawable) row0.getBackground();
            assertTrue(row0Bg.getCornerRadius() > 0f);
            assertEquals(mocha.surfaceContainerHigh, row0Bg.getColor().getDefaultColor());

            TextView row0Title = row0.findViewById(R.id.drawer_ssh_host_title);
            TextView row0Subtitle = row0.findViewById(R.id.drawer_ssh_host_subtitle);
            TextView row0PinBadge = row0.findViewById(R.id.drawer_ssh_host_pin_badge);
            TextView row0AuthBadge = row0.findViewById(R.id.drawer_ssh_host_auth_badge);
            assertEquals(mocha.onSurface, row0Title.getCurrentTextColor());
            assertEquals(mocha.onSurfaceVariant, row0Subtitle.getCurrentTextColor());
            assertTrue(row0PinBadge.getBackground() instanceof GradientDrawable);
            assertEquals(mocha.accent,
                ((GradientDrawable) row0PinBadge.getBackground()).getColor().getDefaultColor());
            assertEquals(mocha.onAccent, row0PinBadge.getCurrentTextColor());
            assertTrue(row0AuthBadge.getBackground() instanceof GradientDrawable);
            assertEquals(mocha.surfaceContainerHigh,
                ((GradientDrawable) row0AuthBadge.getBackground()).getColor().getDefaultColor());
            assertEquals(mocha.onSurfaceVariant, row0AuthBadge.getCurrentTextColor());

            // Live switch to Catppuccin Latte + FiraCode in-place without recreating TermuxActivity
            TermuxThemeManager.setSelectedThemeId(activity, TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
            TermuxFontManager.setSelectedFontId(activity, TermuxFontManager.FONT_FIRA_CODE);
            activity.applyCurrentAppearance();

            assertFalse(activity.isFinishing());
            assertFalse(activity.isDestroyed());

            TermuxColorPalette latte = TermuxThemePresets.getBuiltInById(
                TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
            Typeface activeTypeface = TermuxFontManager.getActiveTypeface(activity);

            assertEquals(ColorUtils.setAlphaComponent(latte.outline, 80),
                ((ColorDrawable) divider.getBackground()).getColor());
            assertEquals(latte.onSurfaceVariant, sectionTitle.getCurrentTextColor());
            assertEquals(latte.accent, manageButton.getCurrentTextColor());

            assertTrue(row0.getBackground() instanceof GradientDrawable);
            GradientDrawable row0LatteBg = (GradientDrawable) row0.getBackground();
            assertTrue(row0LatteBg.getCornerRadius() > 0f);
            assertEquals(latte.surfaceContainerHigh, row0LatteBg.getColor().getDefaultColor());
            assertEquals(latte.onSurface, row0Title.getCurrentTextColor());
            assertEquals(latte.onSurfaceVariant, row0Subtitle.getCurrentTextColor());
            assertEquals(activeTypeface, row0Subtitle.getTypeface());
            assertEquals(latte.accent,
                ((GradientDrawable) row0PinBadge.getBackground()).getColor().getDefaultColor());
            assertEquals(latte.onAccent, row0PinBadge.getCurrentTextColor());
            assertEquals(latte.surfaceContainerHigh,
                ((GradientDrawable) row0AuthBadge.getBackground()).getColor().getDefaultColor());
            assertEquals(latte.onSurfaceVariant, row0AuthBadge.getCurrentTextColor());

            View row1 = hostsList.getChildAt(1);
            assertTrue(row1.getBackground() instanceof GradientDrawable);
            GradientDrawable row1LatteBg = (GradientDrawable) row1.getBackground();
            assertTrue(row1LatteBg.getCornerRadius() > 0f);
            assertEquals(latte.surfaceContainer, row1LatteBg.getColor().getDefaultColor());
            assertEquals(latte.onSurface,
                ((TextView) row1.findViewById(R.id.drawer_ssh_host_title)).getCurrentTextColor());

            // Also verify empty state is themed live when resuming with no hosts under Dracula
            controller.pause().stop();
            repository.deleteProfile(pinnedKey.id);
            repository.deleteProfile(recentPw.id);
            TermuxThemeManager.setSelectedThemeId(activity, TermuxColorPalette.THEME_DRACULA);
            controller.start().resume();

            TermuxColorPalette dracula = TermuxThemePresets.getBuiltInById(
                TermuxColorPalette.THEME_DRACULA);
            assertEquals(View.VISIBLE, emptyState.getVisibility());
            assertTrue(emptyState.getBackground() instanceof GradientDrawable);
            assertTrue(((GradientDrawable) emptyState.getBackground()).getCornerRadius() > 0f);
            assertEquals(dracula.onSurfaceVariant, emptyState.getCurrentTextColor());
        }
    }
}
