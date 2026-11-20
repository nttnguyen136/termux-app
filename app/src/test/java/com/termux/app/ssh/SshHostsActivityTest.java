package com.termux.app.ssh;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.termux.R;
import com.termux.shared.termux.theme.TermuxAppearancePreferences;
import com.termux.shared.termux.theme.TermuxColorPalette;
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
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SshHostsActivityTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    private Context appContext;
    private File prefixDir;
    private File homeDir;
    private File tmpDir;
    private File storageDir;
    private SshProfileRepository repository;
    private List<SshSessionLaunchRequest> launchedRequests;
    private boolean launchShouldSucceed;
    private SshKeygenRunner keygenRunner;
    private java.util.concurrent.Executor keygenExecutor;

    @Before
    public void setUp() throws Exception {
        appContext = RuntimeEnvironment.getApplication();
        resetGlobalAppearanceState(appContext);
        TermuxThemeManager.setSelectedThemeId(appContext, TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);

        prefixDir = tempFolder.newFolder("prefix");
        homeDir = tempFolder.newFolder("home");
        tmpDir = tempFolder.newFolder("tmp");
        storageDir = tempFolder.newFolder("storage");
        repository = new SshProfileRepository(storageDir, AesGcmSshSecretCipher.withRandomKey());
        launchedRequests = new ArrayList<>();
        launchShouldSucceed = true;
        keygenRunner = (pfx, home, bin, args) ->
            SshKeygenRunner.ExecutionResult.failure(1, "", "Stub keygen runner");
        keygenExecutor = Runnable::run;

        SshHostsActivity.setEnvironmentFactoryForTests(activity -> new SshHostsEnvironment(
            repository,
            prefixDir,
            homeDir,
            tmpDir,
            request -> {
                launchedRequests.add(request);
                return launchShouldSucceed;
            },
            keygenRunner,
            () -> 1_800_000_000_000L,
            keygenExecutor
        ));
    }

    @After
    public void tearDown() {
        SshHostsActivity.setEnvironmentFactoryForTests(null);
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
    public void launchViaCreateIntent_appliesPaletteChromeAndUpdatesLiveOnResume() {
        Intent intent = SshHostsActivity.createIntent(appContext);
        assertEquals(SshHostsActivity.class.getName(), intent.getComponent().getClassName());

        try (ActivityController<SshHostsActivity> controller =
                 Robolectric.buildActivity(SshHostsActivity.class, intent)) {
            SshHostsActivity activity = controller.create().start().resume().get();

            TermuxColorPalette mocha = TermuxThemePresets.getBuiltInById(
                TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);

            Window window = activity.getWindow();
            assertEquals(mocha.statusBarColor, window.getStatusBarColor());
            assertEquals(mocha.navigationBarColor, window.getNavigationBarColor());
            WindowInsetsControllerCompat insets = WindowCompat.getInsetsController(window, window.getDecorView());
            assertFalse(insets.isAppearanceLightStatusBars());

            Toolbar toolbar = activity.findViewById(com.termux.shared.R.id.toolbar);
            assertNotNull(toolbar);
            assertEquals(activity.getString(R.string.title_activity_ssh_hosts), toolbar.getTitle().toString());
            assertEquals(mocha.surface, ((ColorDrawable) toolbar.getBackground()).getColor());

            View root = activity.findViewById(R.id.ssh_hosts_root);
            assertNotNull(root);
            assertEquals(mocha.surface, ((ColorDrawable) root.getBackground()).getColor());

            View emptyState = activity.findViewById(R.id.ssh_empty_state_container);
            assertNotNull(emptyState);
            assertEquals(View.VISIBLE, emptyState.getVisibility());

            TextView emptyTitle = activity.findViewById(R.id.ssh_empty_title);
            assertNotNull(emptyTitle);
            assertEquals(mocha.onSurface, emptyTitle.getCurrentTextColor());

            // Switch to Catppuccin Latte while paused and resume -> live chrome & surface update
            controller.pause().stop();
            TermuxThemeManager.setSelectedThemeId(appContext, TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
            controller.start().resume();

            TermuxColorPalette latte = TermuxThemePresets.getBuiltInById(
                TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
            assertEquals(latte.statusBarColor, window.getStatusBarColor());
            assertEquals(latte.navigationBarColor, window.getNavigationBarColor());
            assertTrue(insets.isAppearanceLightStatusBars());
            assertEquals(latte.surface, ((ColorDrawable) toolbar.getBackground()).getColor());
            assertEquals(latte.surface, ((ColorDrawable) root.getBackground()).getColor());
            assertEquals(latte.onSurface, emptyTitle.getCurrentTextColor());
        }
    }

    @Test
    public void rendersSortedHostCardsAndFiltersLiveByNameHostAndUsername() throws Exception {
        SshProfile beta = SshProfile.builder()
            .id("id-beta")
            .displayName("Beta API")
            .host("beta.internal")
            .port(2222)
            .username("deploy")
            .authType(SshAuthType.PASSWORD)
            .pinned(false)
            .lastUsedAtMillis(5000L)
            .build();
        SshProfile alpha = SshProfile.builder()
            .id("id-alpha")
            .displayName("Alpha Bastion")
            .host("alpha.internal")
            .port(22)
            .username("ops")
            .authType(SshAuthType.SSH_KEY)
            .privateKeyPath(new File(homeDir, ".ssh/id_ed25519").getAbsolutePath())
            .pinned(true)
            .lastUsedAtMillis(1000L)
            .build();
        SshProfile gamma = SshProfile.builder()
            .id("id-gamma")
            .displayName("Gamma DB")
            .host("gamma.internal")
            .port(22)
            .username("dbadmin")
            .authType(SshAuthType.SSH_KEY)
            .pinned(false)
            .lastUsedAtMillis(2000L)
            .build();

        repository.saveProfile(beta);
        repository.saveProfile(alpha);
        repository.saveProfile(gamma);

        try (ActivityController<SshHostsActivity> controller =
                 Robolectric.buildActivity(SshHostsActivity.class, SshHostsActivity.createIntent(appContext))) {
            SshHostsActivity activity = controller.create().start().resume().get();

            android.widget.LinearLayout listContainer = activity.findViewById(R.id.ssh_hosts_list_container);
            View emptyState = activity.findViewById(R.id.ssh_empty_state_container);
            assertEquals(View.VISIBLE, listContainer.getVisibility());
            assertEquals(View.GONE, emptyState.getVisibility());
            assertEquals(3, listContainer.getChildCount());

            // Pinned Alpha first, then Beta (newer lastUsedAtMillis), then Gamma
            View card0 = listContainer.getChildAt(0);
            assertEquals("Alpha Bastion", ((TextView) card0.findViewById(R.id.ssh_host_display_name)).getText().toString());
            assertEquals("ops@alpha.internal", ((TextView) card0.findViewById(R.id.ssh_host_endpoint)).getText().toString());
            assertEquals(activity.getString(R.string.ssh_auth_type_key), ((TextView) card0.findViewById(R.id.ssh_host_auth_badge)).getText().toString());
            assertEquals(View.VISIBLE, card0.findViewById(R.id.ssh_host_pinned_badge).getVisibility());

            View card1 = listContainer.getChildAt(1);
            assertEquals("Beta API", ((TextView) card1.findViewById(R.id.ssh_host_display_name)).getText().toString());
            assertEquals("deploy@beta.internal:2222", ((TextView) card1.findViewById(R.id.ssh_host_endpoint)).getText().toString());
            assertEquals(activity.getString(R.string.ssh_auth_type_password), ((TextView) card1.findViewById(R.id.ssh_host_auth_badge)).getText().toString());
            assertEquals(View.GONE, card1.findViewById(R.id.ssh_host_pinned_badge).getVisibility());

            View card2 = listContainer.getChildAt(2);
            assertEquals("Gamma DB", ((TextView) card2.findViewById(R.id.ssh_host_display_name)).getText().toString());

            // Search by display name, username, and host
            android.widget.EditText searchInput = activity.findViewById(R.id.ssh_search_input);
            View clearBtn = activity.findViewById(R.id.ssh_search_clear_button);
            assertEquals(View.GONE, clearBtn.getVisibility());

            searchInput.setText("gamma");
            assertEquals(View.VISIBLE, clearBtn.getVisibility());
            assertEquals(1, listContainer.getChildCount());
            assertEquals("Gamma DB", ((TextView) listContainer.getChildAt(0).findViewById(R.id.ssh_host_display_name)).getText().toString());

            searchInput.setText("deploy");
            assertEquals(1, listContainer.getChildCount());
            assertEquals("Beta API", ((TextView) listContainer.getChildAt(0).findViewById(R.id.ssh_host_display_name)).getText().toString());

            searchInput.setText("alpha.internal");
            assertEquals(1, listContainer.getChildCount());
            assertEquals("Alpha Bastion", ((TextView) listContainer.getChildAt(0).findViewById(R.id.ssh_host_display_name)).getText().toString());

            // No matches -> empty search state with Clear Search CTA
            searchInput.setText("does-not-exist");
            assertEquals(View.GONE, listContainer.getVisibility());
            assertEquals(View.VISIBLE, emptyState.getVisibility());
            assertEquals(activity.getString(R.string.ssh_empty_search_title), ((TextView) activity.findViewById(R.id.ssh_empty_title)).getText().toString());

            activity.findViewById(R.id.ssh_empty_primary_cta).performClick();
            assertEquals("", searchInput.getText().toString());
            assertEquals(View.VISIBLE, listContainer.getVisibility());
            assertEquals(3, listContainer.getChildCount());

            // Search clear button restores all hosts too
            searchInput.setText("beta");
            assertEquals(1, listContainer.getChildCount());
            clearBtn.performClick();
            assertEquals(3, listContainer.getChildCount());
        }
    }

    @Test
    public void pinUnpinDuplicateAndDeleteWithConfirmationPurgesDeletedSecret() throws Exception {
        SshProfile staging = SshProfile.builder()
            .id("id-staging")
            .displayName("Staging")
            .host("staging.internal")
            .username("deploy")
            .authType(SshAuthType.PASSWORD)
            .pinned(false)
            .lastUsedAtMillis(5000L)
            .build();
        SshProfile prod = SshProfile.builder()
            .id("id-prod")
            .displayName("Prod")
            .host("prod.internal")
            .username("ops")
            .authType(SshAuthType.SSH_KEY)
            .pinned(true)
            .lastUsedAtMillis(1000L)
            .build();
        repository.saveProfileWithSecret(staging, "staging-secret");
        repository.saveProfile(prod);

        try (ActivityController<SshHostsActivity> controller =
                 Robolectric.buildActivity(SshHostsActivity.class, SshHostsActivity.createIntent(appContext))) {
            SshHostsActivity activity = controller.create().start().resume().get();
            android.widget.LinearLayout listContainer = activity.findViewById(R.id.ssh_hosts_list_container);
            assertEquals(2, listContainer.getChildCount());
            assertEquals("Prod", ((TextView) listContainer.getChildAt(0).findViewById(R.id.ssh_host_display_name)).getText().toString());
            assertEquals("Staging", ((TextView) listContainer.getChildAt(1).findViewById(R.id.ssh_host_display_name)).getText().toString());

            // Pin Staging -> moves above Prod and persists
            listContainer.getChildAt(1).findViewById(R.id.ssh_host_pin_button).performClick();
            assertTrue(repository.getById(staging.id).pinned);
            assertEquals("Staging", ((TextView) listContainer.getChildAt(0).findViewById(R.id.ssh_host_display_name)).getText().toString());

            // Unpin Staging -> moves back below Prod
            listContainer.getChildAt(0).findViewById(R.id.ssh_host_pin_button).performClick();
            assertFalse(repository.getById(staging.id).pinned);
            assertEquals("Prod", ((TextView) listContainer.getChildAt(0).findViewById(R.id.ssh_host_display_name)).getText().toString());
            assertEquals("Staging", ((TextView) listContainer.getChildAt(1).findViewById(R.id.ssh_host_display_name)).getText().toString());

            // Duplicate Staging -> creates Staging (Copy) with independent copy of secret
            listContainer.getChildAt(1).findViewById(R.id.ssh_host_duplicate_button).performClick();
            assertEquals(3, listContainer.getChildCount());
            SshProfile copy = null;
            for (SshProfile p : repository.getAllSorted()) {
                if ("Staging (Copy)".equals(p.displayName)) {
                    copy = p;
                }
            }
            assertNotNull(copy);
            assertFalse(copy.pinned);
            assertEquals("staging-secret", repository.getSecret(copy.id));

            // Find Staging (Copy) card index
            int copyIdx = -1;
            for (int i = 0; i < listContainer.getChildCount(); i++) {
                String name = ((TextView) listContainer.getChildAt(i).findViewById(R.id.ssh_host_display_name)).getText().toString();
                if ("Staging (Copy)".equals(name)) {
                    copyIdx = i;
                    break;
                }
            }
            assertTrue(copyIdx >= 0);

            // Delete Staging (Copy) -> Cancel preserves it
            listContainer.getChildAt(copyIdx).findViewById(R.id.ssh_host_delete_button).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(dialog instanceof androidx.appcompat.app.AlertDialog);
            androidx.appcompat.app.AlertDialog alert = (androidx.appcompat.app.AlertDialog) dialog;
            assertTrue(alert.isShowing());

            TermuxColorPalette mocha = TermuxThemePresets.getBuiltInById(
                TermuxColorPalette.THEME_CATPPUCCIN_MOCHA);
            android.graphics.drawable.Drawable bg = alert.getWindow().getDecorView().getBackground();
            assertTrue(bg instanceof android.graphics.drawable.GradientDrawable);
            android.graphics.drawable.GradientDrawable windowBg = (android.graphics.drawable.GradientDrawable) bg;
            assertEquals(mocha.surfaceContainerHigh, windowBg.getColor().getDefaultColor());
            assertEquals(
                24f * activity.getResources().getDisplayMetrics().density,
                windowBg.getCornerRadius(),
                0.5f
            );
            assertEquals(
                mocha.onSurface,
                ((TextView) alert.findViewById(androidx.appcompat.R.id.alertTitle)).getCurrentTextColor()
            );
            assertEquals(
                mocha.onSurfaceVariant,
                ((TextView) alert.findViewById(android.R.id.message)).getCurrentTextColor()
            );
            assertEquals(
                mocha.error,
                alert.getButton(android.content.DialogInterface.BUTTON_POSITIVE).getCurrentTextColor()
            );
            assertEquals(
                mocha.onSurfaceVariant,
                alert.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).getCurrentTextColor()
            );

            alert.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(alert.isShowing());
            assertEquals(3, listContainer.getChildCount());
            assertNotNull(repository.getById(copy.id));

            // Delete Staging (Copy) -> Confirm deletes profile and purges secret, keeping original secret intact
            listContainer.getChildAt(copyIdx).findViewById(R.id.ssh_host_delete_button).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            androidx.appcompat.app.AlertDialog confirmAlert =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(confirmAlert.isShowing());
            confirmAlert.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(confirmAlert.isShowing());

            assertEquals(2, listContainer.getChildCount());
            assertEquals(null, repository.getById(copy.id));
            assertFalse(repository.hasSecret(copy.id));
            assertEquals("staging-secret", repository.getSecret(staging.id));
        }
    }

    @Test
    public void addAndEditProfileDialog_enforcesDefaultsValidationMaskedSecretsAndCollapsibleAdvanced()
            throws Exception {
        SshProfile simple = SshProfile.builder()
            .id("id-simple")
            .displayName("Simple Box")
            .host("simple.internal")
            .username("alice")
            .authType(SshAuthType.SSH_KEY)
            .build();
        repository.saveProfile(simple);

        try (ActivityController<SshHostsActivity> controller =
                 Robolectric.buildActivity(SshHostsActivity.class, SshHostsActivity.createIntent(appContext))) {
            SshHostsActivity activity = controller.create().start().resume().get();

            // 1. Open Add Host dialog -> verify defaults (SSH_KEY, port 22, masked secret, collapsed Advanced)
            activity.findViewById(R.id.ssh_add_host_button).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            androidx.appcompat.app.AlertDialog addDialog =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull(addDialog);
            assertTrue(addDialog.isShowing());

            android.widget.EditText hostInput = addDialog.findViewById(R.id.ssh_form_host);
            android.widget.EditText portInput = addDialog.findViewById(R.id.ssh_form_port);
            android.widget.EditText userInput = addDialog.findViewById(R.id.ssh_form_username);
            android.widget.EditText displayNameInput = addDialog.findViewById(R.id.ssh_form_display_name);
            android.widget.EditText secretInput = addDialog.findViewById(R.id.ssh_form_secret_input);
            View keySection = addDialog.findViewById(R.id.ssh_form_key_section);
            View advancedContainer = addDialog.findViewById(R.id.ssh_form_advanced_container);

            assertEquals("22", portInput.getText().toString());
            assertEquals(View.VISIBLE, keySection.getVisibility());
            assertEquals(View.GONE, advancedContainer.getVisibility());
            assertTrue(secretInput.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod);

            // Invalid save -> dialog stays open and displays inline errors
            hostInput.setText("");
            userInput.setText("");
            portInput.setText("70000");
            addDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertTrue(addDialog.isShowing());
            TextView hostError = addDialog.findViewById(R.id.ssh_form_host_error);
            TextView userError = addDialog.findViewById(R.id.ssh_form_username_error);
            TextView portError = addDialog.findViewById(R.id.ssh_form_port_error);
            assertEquals(View.VISIBLE, hostError.getVisibility());
            assertEquals(View.VISIBLE, userError.getVisibility());
            assertEquals(View.VISIBLE, portError.getVisibility());
            assertEquals(1, repository.getAllSorted().size());

            // Fill valid basic fields + switch to Password + expand Advanced with invalid negative keepAlive first
            displayNameInput.setText("Prod DB");
            hostInput.setText("db.internal");
            portInput.setText("2202");
            userInput.setText("postgres");
            addDialog.findViewById(R.id.ssh_form_auth_password_button).performClick();
            assertEquals(View.GONE, keySection.getVisibility());
            secretInput.setText("db-secret-99");

            addDialog.findViewById(R.id.ssh_form_advanced_toggle).performClick();
            assertEquals(View.VISIBLE, advancedContainer.getVisibility());
            ((android.widget.EditText) addDialog.findViewById(R.id.ssh_form_startup_command)).setText("psql");
            ((android.widget.EditText) addDialog.findViewById(R.id.ssh_form_proxy_jump)).setText("bastion.internal");
            android.widget.EditText keepAliveInput = addDialog.findViewById(R.id.ssh_form_keep_alive);
            keepAliveInput.setText("-5");
            ((android.widget.EditText) addDialog.findViewById(R.id.ssh_form_extra_args)).setText("-A -C");

            addDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertTrue(addDialog.isShowing());
            assertEquals(View.VISIBLE, addDialog.findViewById(R.id.ssh_form_keep_alive_error).getVisibility());

            // Fix keepAlive and save
            keepAliveInput.setText("45");
            addDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(addDialog.isShowing());

            List<SshProfile> all = repository.getAllSorted();
            assertEquals(2, all.size());
            SshProfile created = null;
            for (SshProfile p : all) {
                if ("db.internal".equals(p.host)) {
                    created = p;
                }
            }
            assertNotNull(created);
            assertEquals("Prod DB", created.displayName);
            assertEquals(2202, created.port);
            assertEquals("postgres", created.username);
            assertEquals(SshAuthType.PASSWORD, created.authType);
            assertEquals("psql", created.startupCommand);
            assertEquals("bastion.internal", created.proxyJump);
            assertEquals(45, created.keepAliveIntervalSeconds);
            assertEquals("-A -C", created.extraArgs);
            assertEquals("db-secret-99", repository.getSecret(created.id));

            // 2. Edit simple profile -> Advanced starts collapsed because its advanced fields are empty
            android.widget.LinearLayout listContainer = activity.findViewById(R.id.ssh_hosts_list_container);
            int simpleIdx = findCardIndexByName(listContainer, "Simple Box");
            assertTrue(simpleIdx >= 0);
            listContainer.getChildAt(simpleIdx).findViewById(R.id.ssh_host_edit_button).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            androidx.appcompat.app.AlertDialog simpleEditDialog =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertEquals(View.GONE, simpleEditDialog.findViewById(R.id.ssh_form_advanced_container).getVisibility());
            simpleEditDialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            // 3. Edit Prod DB -> Advanced auto-expanded, secret masked by default, reveal toggle works,
            // editing displayName alone preserves saved secret!
            int dbIdx = findCardIndexByName(listContainer, "Prod DB");
            assertTrue(dbIdx >= 0);
            listContainer.getChildAt(dbIdx).findViewById(R.id.ssh_host_edit_button).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            androidx.appcompat.app.AlertDialog editDialog =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(editDialog.isShowing());
            assertEquals(View.VISIBLE, editDialog.findViewById(R.id.ssh_form_advanced_container).getVisibility());

            android.widget.EditText editSecret = editDialog.findViewById(R.id.ssh_form_secret_input);
            assertTrue(editSecret.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod);
            editDialog.findViewById(R.id.ssh_form_secret_toggle_button).performClick();
            assertFalse(editSecret.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod);
            editDialog.findViewById(R.id.ssh_form_secret_toggle_button).performClick();
            assertTrue(editSecret.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod);

            ((android.widget.EditText) editDialog.findViewById(R.id.ssh_form_display_name)).setText("Primary DB");
            editDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(editDialog.isShowing());

            assertEquals("Primary DB", repository.getById(created.id).displayName);
            assertEquals("db-secret-99", repository.getSecret(created.id));

            // 4. Re-open Edit and clear saved secret -> purged from SshCredentialStore
            int updatedIdx = findCardIndexByName(listContainer, "Primary DB");
            listContainer.getChildAt(updatedIdx).findViewById(R.id.ssh_host_edit_button).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            androidx.appcompat.app.AlertDialog clearDialog =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            clearDialog.findViewById(R.id.ssh_form_secret_clear_button).performClick();
            clearDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertFalse(repository.hasSecret(created.id));
        }
    }

    @Test
    public void editProfileDialog_authTypeToggleRoundTripPreservesSecretUnlessEditedOrCleared()
            throws Exception {
        SshProfile passwordHost = SshProfile.builder()
            .id("id-roundtrip-pw")
            .displayName("PW Roundtrip")
            .host("pw.internal")
            .username("deploy")
            .authType(SshAuthType.PASSWORD)
            .build();
        repository.saveProfileWithSecret(passwordHost, "roundtrip-pw");

        SshProfile keyHost = SshProfile.builder()
            .id("id-roundtrip-key")
            .displayName("Key Roundtrip")
            .host("key.internal")
            .username("ops")
            .authType(SshAuthType.SSH_KEY)
            .build();
        repository.saveProfileWithSecret(keyHost, "roundtrip-passphrase");

        try (ActivityController<SshHostsActivity> controller =
                 Robolectric.buildActivity(SshHostsActivity.class, SshHostsActivity.createIntent(appContext))) {
            SshHostsActivity activity = controller.create().start().resume().get();
            android.widget.LinearLayout listContainer = activity.findViewById(R.id.ssh_hosts_list_container);

            // 1. PASSWORD -> SSH_KEY -> PASSWORD round-trip without editing/clearing preserves secret
            int pwIdx = findCardIndexByName(listContainer, "PW Roundtrip");
            assertTrue(pwIdx >= 0);
            listContainer.getChildAt(pwIdx).findViewById(R.id.ssh_host_edit_button).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            androidx.appcompat.app.AlertDialog pwDialog =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            android.widget.EditText pwSecretInput = pwDialog.findViewById(R.id.ssh_form_secret_input);
            TextView pwSecretStatus = pwDialog.findViewById(R.id.ssh_form_secret_status);

            pwDialog.findViewById(R.id.ssh_form_auth_key_button).performClick();
            assertEquals("", pwSecretInput.getText().toString());
            assertEquals(View.GONE, pwSecretStatus.getVisibility());

            pwDialog.findViewById(R.id.ssh_form_auth_password_button).performClick();
            assertTrue(pwSecretInput.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod);
            assertEquals("roundtrip-pw", pwSecretInput.getText().toString());
            assertEquals(View.VISIBLE, pwSecretStatus.getVisibility());
            assertEquals(
                activity.getString(R.string.ssh_form_secret_kept_status),
                pwSecretStatus.getText().toString()
            );

            pwDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(pwDialog.isShowing());
            assertEquals("roundtrip-pw", repository.getSecret(passwordHost.id));

            // 2. SSH_KEY -> PASSWORD -> SSH_KEY round-trip preserves passphrase
            int keyIdx = findCardIndexByName(listContainer, "Key Roundtrip");
            assertTrue(keyIdx >= 0);
            listContainer.getChildAt(keyIdx).findViewById(R.id.ssh_host_edit_button).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            androidx.appcompat.app.AlertDialog keyDialog =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            android.widget.EditText keySecretInput = keyDialog.findViewById(R.id.ssh_form_secret_input);
            keyDialog.findViewById(R.id.ssh_form_auth_password_button).performClick();
            assertEquals("", keySecretInput.getText().toString());

            keyDialog.findViewById(R.id.ssh_form_auth_key_button).performClick();
            assertTrue(keySecretInput.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod);
            assertEquals("roundtrip-passphrase", keySecretInput.getText().toString());

            keyDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals("roundtrip-passphrase", repository.getSecret(keyHost.id));

            // 3. Explicit Clear before round-trip still purges secret on Save
            pwIdx = findCardIndexByName(listContainer, "PW Roundtrip");
            listContainer.getChildAt(pwIdx).findViewById(R.id.ssh_host_edit_button).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            androidx.appcompat.app.AlertDialog clearRoundtripDialog =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            clearRoundtripDialog.findViewById(R.id.ssh_form_secret_clear_button).performClick();
            clearRoundtripDialog.findViewById(R.id.ssh_form_auth_key_button).performClick();
            clearRoundtripDialog.findViewById(R.id.ssh_form_auth_password_button).performClick();
            assertEquals("", ((android.widget.EditText) clearRoundtripDialog.findViewById(R.id.ssh_form_secret_input)).getText().toString());
            clearRoundtripDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(repository.hasSecret(passwordHost.id));
        }
    }

    private static int findCardIndexByName(android.widget.LinearLayout listContainer, String expectedName) {
        for (int i = 0; i < listContainer.getChildCount(); i++) {
            TextView tv = listContainer.getChildAt(i).findViewById(R.id.ssh_host_display_name);
            if (tv != null && expectedName.equals(tv.getText().toString())) {
                return i;
            }
        }
        return -1;
    }

    @Test
    public void keyPickerSafImportEd25519GenerationAndCopyPublicKeyWorkEndToEnd() throws Exception {
        File sshDir = new File(homeDir, ".ssh");
        assertTrue(sshDir.mkdirs());
        File existingKey = new File(sshDir, "work_rsa");
        java.nio.file.Files.write(existingKey.toPath(), "EXISTING_RSA".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        File existingPub = new File(sshDir, "work_rsa.pub");
        java.nio.file.Files.write(existingPub.toPath(), "ssh-rsa AAAAB3Work work@host\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Register a SAF content stream for ACTION_OPEN_DOCUMENT result
        android.net.Uri safUri = android.net.Uri.parse("content://com.example.docs/keys/imported_staging_key");
        org.robolectric.Shadows.shadowOf(appContext.getContentResolver()).registerInputStream(
            safUri,
            new java.io.ByteArrayInputStream("-----BEGIN OPENSSH PRIVATE KEY-----\nSAF_IMPORTED\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );

        final String generatedPubContent = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGenUiKey termux-ssh";
        final boolean[] keygenCalled = {false};
        keygenRunner = (pfx, home, bin, args) -> {
            keygenCalled[0] = true;
            String targetPath = args.get(args.indexOf("-f") + 1);
            try {
                java.nio.file.Files.write(new File(targetPath).toPath(), "PRIV_ED25519".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                java.nio.file.Files.write(new File(targetPath + ".pub").toPath(), (generatedPubContent + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
            return SshKeygenRunner.ExecutionResult.success("ok", "");
        };

        try (ActivityController<SshHostsActivity> controller =
                 Robolectric.buildActivity(SshHostsActivity.class, SshHostsActivity.createIntent(appContext))) {
            SshHostsActivity activity = controller.create().start().resume().get();

            // Open Add Host form -> key spinner includes default + work_rsa
            activity.findViewById(R.id.ssh_add_host_button).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            androidx.appcompat.app.AlertDialog formDialog =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            android.widget.Spinner keySpinner = formDialog.findViewById(R.id.ssh_form_key_spinner);
            assertEquals(2, keySpinner.getAdapter().getCount());
            assertEquals("work_rsa", keySpinner.getAdapter().getItem(1).toString());

            // SAF Import Key -> starts ACTION_OPEN_DOCUMENT and imports with 0600 permissions
            formDialog.findViewById(R.id.ssh_form_import_key_button).performClick();
            org.robolectric.shadows.ShadowActivity shadowActivity = org.robolectric.Shadows.shadowOf(activity);
            org.robolectric.shadows.ShadowActivity.IntentForResult safRequest =
                shadowActivity.getNextStartedActivityForResult();
            assertNotNull(safRequest);
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, safRequest.intent.getAction());

            Intent safResult = new Intent().setData(safUri);
            shadowActivity.receiveResult(safRequest.intent, android.app.Activity.RESULT_OK, safResult);
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            File importedFile = new File(sshDir, "imported_staging_key");
            assertTrue(importedFile.isFile());
            assertEquals(
                java.util.EnumSet.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
                ),
                java.nio.file.Files.getPosixFilePermissions(importedFile.toPath())
            );
            assertEquals("imported_staging_key", keySpinner.getSelectedItem().toString());

            // Try Generate Ed25519 while ssh-keygen is missing -> shows missing OpenSSH dialog
            formDialog.findViewById(R.id.ssh_form_generate_key_button).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            androidx.appcompat.app.AlertDialog missingKeygenDialog =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull(missingKeygenDialog);
            assertTrue(missingKeygenDialog.isShowing());
            assertEquals(
                activity.getString(R.string.ssh_keys_generate_missing_openssh_title),
                ((TextView) missingKeygenDialog.findViewById(androidx.appcompat.R.id.alertTitle)).getText().toString()
            );
            assertFalse(keygenCalled[0]);

            // Confirm Install OpenSSH -> launches pkg install -y openssh
            missingKeygenDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(1, launchedRequests.size());
            assertTrue(launchedRequests.get(0).arguments.get(1).contains("pkg install -y openssh"));

            // Now create executable $PREFIX/bin/ssh-keygen and generate Ed25519 key
            File binDir = new File(prefixDir, "bin");
            assertTrue(binDir.mkdirs());
            File sshKeygen = new File(binDir, "ssh-keygen");
            assertTrue(sshKeygen.createNewFile());
            assertTrue(sshKeygen.setExecutable(true, true));

            formDialog.findViewById(R.id.ssh_form_generate_key_button).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            androidx.appcompat.app.AlertDialog keygenPrompt =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(keygenPrompt.isShowing());
            ((android.widget.EditText) keygenPrompt.findViewById(R.id.ssh_keygen_name_input)).setText("my_ed25519");
            keygenPrompt.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertTrue(keygenCalled[0]);
            assertEquals("my_ed25519", keySpinner.getSelectedItem().toString());
            View copyPubBtn = formDialog.findViewById(R.id.ssh_form_copy_pub_key_button);
            assertEquals(View.VISIBLE, copyPubBtn.getVisibility());
            copyPubBtn.performClick();

            android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            assertNotNull(clipboard.getPrimaryClip());
            assertEquals(generatedPubContent, clipboard.getPrimaryClip().getItemAt(0).getText().toString());

            formDialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            // Also verify top-level Key Tools dialog lists keys and copies .pub
            activity.findViewById(R.id.ssh_action_key_tools).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            androidx.appcompat.app.AlertDialog keyToolsDialog =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(keyToolsDialog.isShowing());
            android.widget.LinearLayout toolsList = keyToolsDialog.findViewById(R.id.ssh_key_tools_list);
            assertEquals(3, toolsList.getChildCount());
            keyToolsDialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
        }
    }

    @Test
    public void ed25519KeyGeneration_runsOffUiThreadAndGuardsDestroyedLifecycle() throws Exception {
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File sshKeygen = new File(binDir, "ssh-keygen");
        assertTrue(sshKeygen.createNewFile());
        assertTrue(sshKeygen.setExecutable(true, true));

        java.util.concurrent.ExecutorService bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        keygenExecutor = bgExecutor;
        try {
            java.util.concurrent.CountDownLatch runnerCompleted = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicBoolean ranOnMainThread =
                new java.util.concurrent.atomic.AtomicBoolean(true);

            keygenRunner = (pfx, home, bin, args) -> {
                ranOnMainThread.set(android.os.Looper.getMainLooper().isCurrentThread());
                String targetPath = args.get(args.indexOf("-f") + 1);
                try {
                    java.nio.file.Files.write(
                        new File(targetPath).toPath(),
                        "PRIV_BG".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    );
                    java.nio.file.Files.write(
                        new File(targetPath + ".pub").toPath(),
                        "ssh-ed25519 AAAABGKey bg@termux\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    );
                    return SshKeygenRunner.ExecutionResult.success("ok", "");
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    runnerCompleted.countDown();
                }
            };

            try (ActivityController<SshHostsActivity> controller =
                     Robolectric.buildActivity(SshHostsActivity.class, SshHostsActivity.createIntent(appContext))) {
                SshHostsActivity activity = controller.create().start().resume().get();

                // Verify production createDefault uses an off-main-thread Executor
                java.util.concurrent.CountDownLatch defaultLatch = new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.atomic.AtomicBoolean defaultOnMain =
                    new java.util.concurrent.atomic.AtomicBoolean(true);
                SshHostsEnvironment.createDefault(activity).keygenExecutor.execute(() -> {
                    defaultOnMain.set(android.os.Looper.getMainLooper().isCurrentThread());
                    defaultLatch.countDown();
                });
                assertTrue(defaultLatch.await(5, java.util.concurrent.TimeUnit.SECONDS));
                assertFalse("Default environment keygenExecutor must run off main thread", defaultOnMain.get());

                activity.findViewById(R.id.ssh_add_host_button).performClick();
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

                androidx.appcompat.app.AlertDialog formDialog =
                    (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
                android.widget.Spinner keySpinner = formDialog.findViewById(R.id.ssh_form_key_spinner);

                formDialog.findViewById(R.id.ssh_form_generate_key_button).performClick();
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

                androidx.appcompat.app.AlertDialog keygenPrompt =
                    (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
                ((android.widget.EditText) keygenPrompt.findViewById(R.id.ssh_keygen_name_input)).setText("bg_ed25519");
                keygenPrompt.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();

                assertTrue(runnerCompleted.await(5, java.util.concurrent.TimeUnit.SECONDS));
                assertFalse("ssh-keygen must execute off the UI thread", ranOnMainThread.get());

                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                assertEquals("bg_ed25519", keySpinner.getSelectedItem().toString());
            }

            // Verify destroyed-activity lifecycle guard: completing background keygen after destroy does not crash
            java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
            keygenRunner = (pfx, home, bin, args) -> {
                entered.countDown();
                try {
                    assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
                return SshKeygenRunner.ExecutionResult.failure(1, "", "aborted");
            };

            try (ActivityController<SshHostsActivity> controller =
                     Robolectric.buildActivity(SshHostsActivity.class, SshHostsActivity.createIntent(appContext))) {
                SshHostsActivity activity = controller.create().start().resume().get();
                activity.findViewById(R.id.ssh_action_key_tools).performClick();
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
                androidx.appcompat.app.AlertDialog toolsDialog =
                    (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
                toolsDialog.findViewById(R.id.ssh_key_tools_generate_button).performClick();
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

                androidx.appcompat.app.AlertDialog keygenPrompt =
                    (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
                keygenPrompt.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
                assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));

                controller.pause().stop().destroy();
                release.countDown();
                assertTrue(finished.await(5, java.util.concurrent.TimeUnit.SECONDS));
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            }
        } finally {
            bgExecutor.shutdownNow();
        }
    }

    @Test
    public void configImportDialogPreviewsDuplicatesAndImportsSelectedReadOnly() throws Exception {
        SshProfile existingProd = SshProfile.builder()
            .id("id-existing-prod")
            .displayName("Production")
            .host("prod.example.com")
            .port(2222)
            .username("deploy")
            .build();
        repository.saveProfile(existingProd);

        TermuxThemeManager.setSelectedThemeId(appContext, TermuxColorPalette.THEME_CATPPUCCIN_LATTE);
        TermuxColorPalette latte = TermuxThemePresets.getBuiltInById(
            TermuxColorPalette.THEME_CATPPUCCIN_LATTE);

        try (ActivityController<SshHostsActivity> controller =
                 Robolectric.buildActivity(SshHostsActivity.class, SshHostsActivity.createIntent(appContext))) {
            SshHostsActivity activity = controller.create().start().resume().get();

            // 1. No ~/.ssh/config yet -> shows empty message dialog themed with Catppuccin Latte
            activity.findViewById(R.id.ssh_action_import_config).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            androidx.appcompat.app.AlertDialog emptyImportDialog =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull(emptyImportDialog);
            assertTrue(emptyImportDialog.isShowing());
            assertEquals(
                activity.getString(R.string.ssh_config_import_empty),
                ((TextView) emptyImportDialog.findViewById(android.R.id.message)).getText().toString()
            );
            assertEquals(
                latte.surfaceContainerHigh,
                ((android.graphics.drawable.GradientDrawable) emptyImportDialog.getWindow().getDecorView().getBackground())
                    .getColor().getDefaultColor()
            );
            assertEquals(
                latte.onSurface,
                ((TextView) emptyImportDialog.findViewById(androidx.appcompat.R.id.alertTitle)).getCurrentTextColor()
            );
            assertEquals(
                latte.onSurfaceVariant,
                ((TextView) emptyImportDialog.findViewById(android.R.id.message)).getCurrentTextColor()
            );
            assertEquals(
                latte.accent,
                emptyImportDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).getCurrentTextColor()
            );
            emptyImportDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            // 2. Write ~/.ssh/config and re-open import dialog
            File dotSsh = new File(homeDir, ".ssh");
            assertTrue(dotSsh.mkdirs());
            File sshConfig = new File(dotSsh, "config");
            String originalConfig =
                "Host *\n" +
                "  ServerAliveInterval 60\n\n" +
                "Host existing-prod\n" +
                "  HostName prod.example.com\n" +
                "  Port 2222\n" +
                "  User deploy\n\n" +
                "Host new-web\n" +
                "  HostName web.internal\n" +
                "  User www\n" +
                "  Port 2202\n" +
                "  IdentityFile ~/.ssh/id_web\n" +
                "  ProxyJump jump.internal\n" +
                "  ServerAliveInterval 30\n\n" +
                "Host no-user-box\n" +
                "  HostName nouser.internal\n";
            java.nio.file.Files.write(sshConfig.toPath(), originalConfig.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            activity.findViewById(R.id.ssh_action_import_config).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            androidx.appcompat.app.AlertDialog importDialog =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull(importDialog);
            assertTrue(importDialog.isShowing());

            android.widget.LinearLayout importList = importDialog.findViewById(R.id.ssh_config_import_list);
            assertEquals(3, importList.getChildCount());

            // Row 0: existing-prod -> duplicate badge visible, unchecked by default
            View dupRow = importList.getChildAt(0);
            assertEquals(View.VISIBLE, dupRow.findViewById(R.id.ssh_import_candidate_duplicate_badge).getVisibility());
            assertFalse(((android.widget.CheckBox) dupRow.findViewById(R.id.ssh_import_candidate_checkbox)).isChecked());

            // Row 1: new-web -> not duplicate, has user -> checked by default
            View newRow = importList.getChildAt(1);
            assertEquals(View.GONE, newRow.findViewById(R.id.ssh_import_candidate_duplicate_badge).getVisibility());
            assertTrue(((android.widget.CheckBox) newRow.findViewById(R.id.ssh_import_candidate_checkbox)).isChecked());

            // Row 2: no-user-box -> missing username hint visible, unchecked by default
            View noUserRow = importList.getChildAt(2);
            assertEquals(View.VISIBLE, noUserRow.findViewById(R.id.ssh_import_candidate_missing_user_hint).getVisibility());
            assertFalse(((android.widget.CheckBox) noUserRow.findViewById(R.id.ssh_import_candidate_checkbox)).isChecked());

            // Confirm import -> imports only new-web, refreshes list, leaves ~/.ssh/config untouched
            importDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(importDialog.isShowing());

            List<SshProfile> saved = repository.getAllSorted();
            assertEquals(2, saved.size());
            SshProfile importedWeb = null;
            for (SshProfile p : saved) {
                if ("new-web".equals(p.displayName)) {
                    importedWeb = p;
                }
            }
            assertNotNull(importedWeb);
            assertEquals("web.internal", importedWeb.host);
            assertEquals(2202, importedWeb.port);
            assertEquals("www", importedWeb.username);
            assertEquals(new File(dotSsh, "id_web").getAbsolutePath(), importedWeb.privateKeyPath);
            assertEquals("jump.internal", importedWeb.proxyJump);
            assertEquals(30, importedWeb.keepAliveIntervalSeconds);

            android.widget.LinearLayout hostsContainer = activity.findViewById(R.id.ssh_hosts_list_container);
            assertEquals(2, hostsContainer.getChildCount());
            assertTrue(findCardIndexByName(hostsContainer, "new-web") >= 0);

            assertEquals(
                originalConfig,
                new String(java.nio.file.Files.readAllBytes(sshConfig.toPath()), java.nio.charset.StandardCharsets.UTF_8)
            );
        }
    }

    @Test
    public void tapCardToConnect_keepsActivityOpenOnMissingOpenSshAndFinishesOnlyWhenLaunched() throws Exception {
        SshProfile host = SshProfile.builder()
            .id("id-connect")
            .displayName("Edge Box")
            .host("edge.internal")
            .port(2222)
            .username("ops")
            .authType(SshAuthType.PASSWORD)
            .build();
        repository.saveProfileWithSecret(host, "edge-secret-pw");

        // 1. Missing $PREFIX/bin/ssh -> prompts Material 3 dialog, activity stays open until Install & Connect
        try (ActivityController<SshHostsActivity> controller =
                 Robolectric.buildActivity(SshHostsActivity.class, SshHostsActivity.createIntent(appContext))) {
            SshHostsActivity activity = controller.create().start().resume().get();
            android.widget.LinearLayout listContainer = activity.findViewById(R.id.ssh_hosts_list_container);
            assertEquals(1, listContainer.getChildCount());

            listContainer.getChildAt(0).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertFalse(activity.isFinishing());
            assertTrue(launchedRequests.isEmpty());
            androidx.appcompat.app.AlertDialog installDialog =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertNotNull(installDialog);
            assertTrue(installDialog.isShowing());
            assertEquals(
                activity.getString(R.string.ssh_missing_openssh_title),
                ((TextView) installDialog.findViewById(R.id.ssh_install_dialog_title)).getText().toString()
            );

            // Cancel keeps activity open
            installDialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(installDialog.isShowing());
            assertFalse(activity.isFinishing());
            assertTrue(launchedRequests.isEmpty());

            // Tap card again and confirm Install & Connect -> launches and finishes activity
            listContainer.getChildAt(0).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            androidx.appcompat.app.AlertDialog secondInstallDialog =
                (androidx.appcompat.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
            assertTrue(secondInstallDialog.isShowing());
            secondInstallDialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();

            assertEquals(1, launchedRequests.size());
            assertTrue(launchedRequests.get(0).arguments.get(1).startsWith("pkg install -y openssh && exec "));
            assertNotNull(launchedRequests.get(0).additionalEnvironment.get(SshAskPassManager.ENV_SSH_ASKPASS));
            assertTrue(activity.isFinishing());
            assertEquals(1_800_000_000_000L, repository.getById(host.id).lastUsedAtMillis);
        }

        // 2. Executable $PREFIX/bin/ssh present, gateway fails -> stays open; succeeds -> finishes
        File binDir = new File(prefixDir, "bin");
        assertTrue(binDir.mkdirs());
        File sshFile = new File(binDir, "ssh");
        assertTrue(sshFile.createNewFile());
        assertTrue(sshFile.setExecutable(true, true));

        launchedRequests.clear();
        launchShouldSucceed = false;
        try (ActivityController<SshHostsActivity> controller =
                 Robolectric.buildActivity(SshHostsActivity.class, SshHostsActivity.createIntent(appContext))) {
            SshHostsActivity activity = controller.create().start().resume().get();
            android.widget.LinearLayout listContainer = activity.findViewById(R.id.ssh_hosts_list_container);

            listContainer.getChildAt(0).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertFalse(activity.isFinishing());

            launchShouldSucceed = true;
            listContainer.getChildAt(0).performClick();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            assertEquals(2, launchedRequests.size());
            assertEquals(sshFile.getAbsolutePath(), launchedRequests.get(1).executablePath);
            assertNotNull(launchedRequests.get(1).additionalEnvironment.get(SshAskPassManager.ENV_SSH_ASKPASS));
            assertTrue(activity.isFinishing());
        }
    }
}
