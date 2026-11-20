package com.termux.app.ssh;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Persists {@link SshProfile} metadata as JSON in app-private storage (completely separate from
 * {@code ~/.ssh/config}) and coordinates encrypted secrets in {@link SshCredentialStore}.
 */
public final class SshProfileRepository {

    public static final String PROFILES_FILENAME = "profiles.json";
    public static final String SECRETS_DIRNAME = "secrets";
    private static final int SCHEMA_VERSION = 1;

    /**
     * Sorts pinned hosts first, then most recently used descending, then display name ascending.
     */
    public static final Comparator<SshProfile> PROFILE_COMPARATOR = (a, b) -> {
        int pinnedCmp = Boolean.compare(b.pinned, a.pinned);
        if (pinnedCmp != 0) {
            return pinnedCmp;
        }
        int lastUsedCmp = Long.compare(b.lastUsedAtMillis, a.lastUsedAtMillis);
        if (lastUsedCmp != 0) {
            return lastUsedCmp;
        }
        String nameA = a.getEffectiveDisplayName();
        String nameB = b.getEffectiveDisplayName();
        int nameIgnoreCaseCmp = nameA.compareToIgnoreCase(nameB);
        if (nameIgnoreCaseCmp != 0) {
            return nameIgnoreCaseCmp;
        }
        int nameCmp = nameA.compareTo(nameB);
        if (nameCmp != 0) {
            return nameCmp;
        }
        return a.id.compareTo(b.id);
    };

    private final File profilesFile;
    private final SshCredentialStore credentialStore;
    private final Object lock = new Object();

    public SshProfileRepository(@NonNull File storageDir, @NonNull SshSecretCipher cipher) {
        this(
            new File(storageDir, PROFILES_FILENAME),
            new SshCredentialStore(new File(storageDir, SECRETS_DIRNAME), cipher)
        );
    }

    public SshProfileRepository(
            @NonNull File profilesFile,
            @NonNull SshCredentialStore credentialStore) {
        this.profilesFile = profilesFile;
        this.credentialStore = credentialStore;
    }

    @NonNull
    public static SshProfileRepository createDefault(@NonNull Context context) {
        File storageDir = AndroidKeystoreSshSecretCipher.getDefaultStorageDir(context);
        return new SshProfileRepository(storageDir, new AndroidKeystoreSshSecretCipher(context));
    }

    @NonNull
    public File getProfilesFile() {
        return profilesFile;
    }

    @NonNull
    public List<SshProfile> getAllSorted() throws IOException {
        synchronized (lock) {
            return sortProfiles(loadProfilesLocked());
        }
    }

    @NonNull
    public List<SshProfile> search(@Nullable String query) throws IOException {
        synchronized (lock) {
            return filterProfiles(loadProfilesLocked(), query);
        }
    }

    @NonNull
    public static List<SshProfile> sortProfiles(@NonNull List<SshProfile> profiles) {
        List<SshProfile> sorted = new ArrayList<>(profiles);
        Collections.sort(sorted, PROFILE_COMPARATOR);
        return sorted;
    }

    @NonNull
    public static List<SshProfile> filterProfiles(
            @NonNull List<SshProfile> profiles,
            @Nullable String query) {
        List<SshProfile> filtered = new ArrayList<>();
        for (SshProfile profile : profiles) {
            if (matchesQuery(profile, query)) {
                filtered.add(profile);
            }
        }
        Collections.sort(filtered, PROFILE_COMPARATOR);
        return filtered;
    }

    public static boolean matchesQuery(@NonNull SshProfile profile, @Nullable String query) {
        if (query == null) {
            return true;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return true;
        }
        return profile.displayName.toLowerCase(Locale.ROOT).contains(needle)
            || profile.host.toLowerCase(Locale.ROOT).contains(needle)
            || profile.username.toLowerCase(Locale.ROOT).contains(needle);
    }

    @Nullable
    public SshProfile getById(@NonNull String profileId) throws IOException {
        synchronized (lock) {
            for (SshProfile profile : loadProfilesLocked()) {
                if (profile.id.equals(profileId)) {
                    return profile;
                }
            }
            return null;
        }
    }

    @NonNull
    public SshProfile saveProfile(@NonNull SshProfile profile) throws IOException {
        SshProfileValidator.requireValid(profile);
        synchronized (lock) {
            return upsertLocked(profile);
        }
    }

    @NonNull
    public SshProfile saveProfileWithSecret(
            @NonNull SshProfile profile,
            @Nullable String secretPlaintext) throws IOException, GeneralSecurityException {
        SshProfileValidator.requireValid(profile);
        synchronized (lock) {
            SshProfile saved = upsertLocked(profile);
            credentialStore.saveSecret(saved.id, secretPlaintext);
            return saved;
        }
    }

    public boolean hasSecret(@NonNull String profileId) {
        return credentialStore.hasSecret(profileId);
    }

    @Nullable
    public String getSecret(@NonNull String profileId)
        throws IOException, GeneralSecurityException {
        return credentialStore.getSecret(profileId);
    }

    @Nullable
    public SshProfile setPinned(@NonNull String profileId, boolean pinned) throws IOException {
        synchronized (lock) {
            List<SshProfile> profiles = loadProfilesLocked();
            for (int i = 0; i < profiles.size(); i++) {
                SshProfile existing = profiles.get(i);
                if (existing.id.equals(profileId)) {
                    SshProfile updated = existing.withPinned(pinned);
                    profiles.set(i, updated);
                    writeProfilesLocked(profiles);
                    return updated;
                }
            }
            return null;
        }
    }

    @Nullable
    public SshProfile markUsed(@NonNull String profileId, long timestampMillis) throws IOException {
        synchronized (lock) {
            List<SshProfile> profiles = loadProfilesLocked();
            for (int i = 0; i < profiles.size(); i++) {
                SshProfile existing = profiles.get(i);
                if (existing.id.equals(profileId)) {
                    SshProfile updated = existing.withLastUsedAtMillis(timestampMillis);
                    profiles.set(i, updated);
                    writeProfilesLocked(profiles);
                    return updated;
                }
            }
            return null;
        }
    }

    @NonNull
    public SshProfile duplicateProfile(@NonNull String profileId)
        throws IOException, GeneralSecurityException {
        synchronized (lock) {
            SshProfile source = getByIdLocked(profileId);
            if (source == null) {
                throw new IllegalArgumentException("SSH profile not found: " + profileId);
            }
            String newId = UUID.randomUUID().toString();
            SshProfile copy = source.toBuilder()
                .id(newId)
                .displayName(source.getEffectiveDisplayName() + " (Copy)")
                .pinned(false)
                .lastUsedAtMillis(0L)
                .build();

            String existingSecret = credentialStore.getSecret(source.id);
            if (existingSecret != null && !existingSecret.isEmpty()) {
                credentialStore.saveSecret(copy.id, existingSecret);
            }
            try {
                return upsertLocked(copy);
            } catch (IOException e) {
                credentialStore.deleteSecret(copy.id);
                throw e;
            }
        }
    }

    public boolean deleteProfile(@NonNull String profileId) throws IOException {
        synchronized (lock) {
            List<SshProfile> profiles = loadProfilesLocked();
            boolean removed = false;
            for (int i = 0; i < profiles.size(); i++) {
                if (profiles.get(i).id.equals(profileId)) {
                    profiles.remove(i);
                    removed = true;
                    break;
                }
            }
            if (!removed) {
                return false;
            }
            writeProfilesLocked(profiles);
            credentialStore.deleteSecret(profileId);
            return true;
        }
    }

    @Nullable
    private SshProfile getByIdLocked(@NonNull String profileId) throws IOException {
        for (SshProfile profile : loadProfilesLocked()) {
            if (profile.id.equals(profileId)) {
                return profile;
            }
        }
        return null;
    }

    @NonNull
    private SshProfile upsertLocked(@NonNull SshProfile profile) throws IOException {
        List<SshProfile> profiles = loadProfilesLocked();
        boolean replaced = false;
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).id.equals(profile.id)) {
                profiles.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            profiles.add(profile);
        }
        writeProfilesLocked(profiles);
        return profile;
    }

    @NonNull
    private List<SshProfile> loadProfilesLocked() throws IOException {
        if (!profilesFile.isFile()) {
            return new ArrayList<>();
        }
        String raw = SshFsUtils.readUtf8(profilesFile).trim();
        if (raw.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray array = root.optJSONArray("profiles");
            if (array == null) {
                return new ArrayList<>();
            }
            List<SshProfile> profiles = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                profiles.add(fromJson(obj));
            }
            return profiles;
        } catch (JSONException e) {
            throw new IOException("Failed to parse SSH profiles JSON", e);
        }
    }

    private void writeProfilesLocked(@NonNull List<SshProfile> profiles) throws IOException {
        try {
            JSONObject root = new JSONObject();
            root.put("version", SCHEMA_VERSION);
            JSONArray array = new JSONArray();
            for (SshProfile profile : profiles) {
                array.put(toJson(profile));
            }
            root.put("profiles", array);
            SshFsUtils.writeUtf8Atomic0600(profilesFile, root.toString(2));
        } catch (JSONException e) {
            throw new IOException("Failed to serialize SSH profiles JSON", e);
        }
    }

    @NonNull
    private static JSONObject toJson(@NonNull SshProfile profile) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", profile.id);
        obj.put("displayName", profile.displayName);
        obj.put("host", profile.host);
        obj.put("port", profile.port);
        obj.put("username", profile.username);
        obj.put("authType", profile.authType.name());
        obj.put("privateKeyPath", profile.privateKeyPath);
        obj.put("pinned", profile.pinned);
        obj.put("lastUsedAtMillis", profile.lastUsedAtMillis);
        obj.put("startupCommand", profile.startupCommand);
        obj.put("proxyJump", profile.proxyJump);
        obj.put("keepAliveIntervalSeconds", profile.keepAliveIntervalSeconds);
        obj.put("extraArgs", profile.extraArgs);
        return obj;
    }

    @NonNull
    private static SshProfile fromJson(@NonNull JSONObject obj) throws JSONException {
        return SshProfile.builder()
            .id(obj.getString("id"))
            .displayName(obj.optString("displayName", ""))
            .host(obj.optString("host", ""))
            .port(obj.optInt("port", SshProfile.DEFAULT_PORT))
            .username(obj.optString("username", ""))
            .authType(SshAuthType.fromString(obj.optString("authType", SshAuthType.SSH_KEY.name())))
            .privateKeyPath(obj.optString("privateKeyPath", ""))
            .pinned(obj.optBoolean("pinned", false))
            .lastUsedAtMillis(obj.optLong("lastUsedAtMillis", 0L))
            .startupCommand(obj.optString("startupCommand", ""))
            .proxyJump(obj.optString("proxyJump", ""))
            .keepAliveIntervalSeconds(obj.optInt("keepAliveIntervalSeconds", 0))
            .extraArgs(obj.optString("extraArgs", ""))
            .build();
    }
}
