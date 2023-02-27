/**
	Communetflix SDK by nkrapivindev.
	Licensed under WTFPL. (aka best license ever).
	just plz credit if possible :3
	
	A stub Netflix SDK implementation for cracking Netflix mobile games.
	
	Is it piracy? - Yes.
	Do I care? - No.
	4PDA? - RU.
	Hotel? - Ural. (krai Permskiy, 614000 Perm, ul. Lenina, d. 58)
	
	Enjoy!
	
	PS: If you don't know how to use this file, it's probably no use for you.
	
*/

package com.netflix.nfgsdk.sdkimpl;

/* -- imports begin -- */
import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.netflix.android.api.cloudsave.CloudSave;
import com.netflix.android.api.cloudsave.CloudSaveResolution;
import com.netflix.android.api.cloudsave.CloudSaveStatus;
import com.netflix.android.api.cloudsave.ConflictResolution;
import com.netflix.android.api.cloudsave.SlotInfo;

import com.netflix.android.api.events.NetflixSdkEventHandler;

import com.netflix.android.api.leaderboard.Leaderboards;
import com.netflix.android.api.common.FetchDirection;
import com.netflix.android.api.common.NetflixSdkComponents;
import com.netflix.android.api.leaderboard.LeaderboardEntry;
import com.netflix.android.api.leaderboard.LeaderboardInfo;
import com.netflix.android.api.leaderboard.LeaderboardPage;
import com.netflix.android.api.leaderboard.LeaderboardStatus;

import com.netflix.android.api.msg.NetflixMessaging;

import com.netflix.android.api.netflixsdk.NetflixSdk;
import com.netflix.android.api.netflixsdk.Locale;
import com.netflix.android.api.netflixsdk.NetflixSdkState;
import com.netflix.android.api.netflixsdk.NetflixProfile;
import com.netflix.android.api.common.CrashReporterConfig;

import com.netflix.android.api.player.NetflixPlayerIdentity;
import com.netflix.android.api.player.PlayerIdentity;
import com.netflix.android.api.player.PlayerIdentityStatus;
import com.netflix.android.api.player.RequestStatus;

import com.netflix.android.api.stats.Stats;
import com.netflix.android.api.stats.AggregatedStat;
import com.netflix.android.api.stats.StatItem;
import com.netflix.android.api.stats.StatsStatus;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import java.nio.charset.Charset;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileFilter;
import java.io.File;
import java.io.FilenameFilter;

import java.lang.Runnable;

import java.lang.System;
import java.lang.Thread;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Handler;
/* -- imports end -- */

/* -- class NetflixGameSdk begin -- */
public final class NetflixGameSdk
	implements NetflixSdkComponents, CloudSave, Leaderboards, Stats, NetflixMessaging, NetflixPlayerIdentity, NetflixSdk, PlayerIdentity, FilenameFilter, Runnable {
	
	/* -- private members begin -- */
	private static final String TAG = "A_NFXSDK";
	private static final String STATE_FILENAME = "netflixstate.kvp";
	private static final String DEFAULT_PROFILE_ID = "4PDA.RU";
	private static final String DEFAULT_COUNTRY = "US";
	private static final String DEFAULT_LANGUAGE = "en";
	private static final String DEFAULT_VARIANT = "";
	private static final String DEFAULT_LOGGING_ID = "dummyloggingid";
	private static final String DEFAULT_TOKEN = "dummynetflixaccesstoken";
	private static final String DEFAULT_SDK_VERSION = "0.8.5+710.c05cfa41"; /* for TMNT */
	private static final String DEFAULT_CRASH_GUID = "5c4bcd90-ef20-4b65-82e4-9fac7f07e3fc";
	private static final String DEFAULT_CRASH_PROJECT_ID = "036c4a7d-2d92-427d-b102-638b70b5dca8";
	private static final String DEFAULT_DESCRIPTION = "Some Description";
	private static final int CURRENT_KVP_VERSION = 1;
	
	private Context applicationContext;
	private NetflixSdkEventHandler eventHandler;
	private NetflixSdkState virtualState;
	private File virtualDirectory;
	private Charset utf8Charset;
	private String virtualSdkVersion;
	private String virtualDescription;
	private CrashReporterConfig virtualCrashReporterConfig;
	private boolean doShowUi;
	private boolean doHideUi;
	private boolean doStateChange;
	private Activity lastActivity;
	private HandlerThread dispatcher;
	private Looper bgLooper;
	private Handler bgHandler;
	/* -- private members end -- */
	
	/* -- private methods begin -- */
	private void throwNetflixUiHidden() {
		if (eventHandler != null) {
			Log.i(TAG, "throwNetflixUiHidden: throwing");
			eventHandler.onNetflixUiHidden();
		} else {
			Log.i(TAG, "throwNetflixUiHidden: eventHandler is null");
		}
	}
	
	private void throwNetflixUiVisible() {
		if (eventHandler != null) {
			Log.i(TAG, "throwNetflixUiVisible: throwing");
			eventHandler.onNetflixUiVisible();
		} else {
			Log.i(TAG, "throwNetflixUiVisible: eventHandler is null");
		}
	}
	
	private void throwUserStateChange() {
		if (eventHandler != null) {
			Log.i(TAG, "throwUserStateChange: throwing");
			eventHandler.onUserStateChange(virtualState);
		} else {
			Log.i(TAG, "throwUserStateChange: eventHandler is null");
		}
	}
	
	private void deleteFile(String fileName) {
		try {
			File file = new File(virtualDirectory, fileName);
			file.delete();
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}
	
	private void writeAllBytesTo(String fileName, byte[] data) {
		OutputStream outputStream = null;
		
		try {
			File file = new File(virtualDirectory, fileName);
			outputStream = new FileOutputStream(file);
			outputStream.write(data);
		} catch (Exception exception) {
			exception.printStackTrace();
		}
		
		try {
			if (outputStream != null) {
				outputStream.close();
				outputStream = null;
			}
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}
	
	private void writeStringTo(String fileName, String data) {
		writeAllBytesTo(fileName, data.getBytes(utf8Charset));
	}
	
	private byte[] readAllBytesFrom(String fileName) {
		byte[] contents = null;
		InputStream inputStream = null;
		
		try {
			File file = new File(virtualDirectory, fileName);
			long flen = file.length();
			/* treat zero-length files as non-existent */
			if (flen <= 0L) {
				return contents;
			}
			
			/* it is very unlikely that we get to store a file larger than 2GB */
			inputStream = new FileInputStream(file);
			contents = new byte[(int) flen];
			inputStream.read(contents);
		} catch (Exception exception) {
			exception.printStackTrace();
		}
		
		try {
			if (inputStream != null) {
				inputStream.close();
				inputStream = null;
			}
		} catch (Exception exception) {
			exception.printStackTrace();
		}
		
		return contents;
	}
	
	private String readStringFrom(String fileName) {
		String string = "";
		byte[] data = readAllBytesFrom(fileName);
		if (data != null) {
			string = new String(data, utf8Charset);
		}
		
		return string;
	}
	
	private void writeSdkState() {
		String kvp = "";
		kvp += "kvp.version=" + Integer.toString(CURRENT_KVP_VERSION) + "\n";
		kvp += "currentProfile.gamerProfileId=" + virtualState.currentProfile.gamerProfileId + "\n";
		kvp += "currentProfile.locale.country=" + virtualState.currentProfile.locale.country + "\n";
		kvp += "currentProfile.locale.language=" + virtualState.currentProfile.locale.language + "\n";
		kvp += "currentProfile.locale.variant=" + virtualState.currentProfile.locale.variant + "\n";
		kvp += "currentProfile.loggingId=" + virtualState.currentProfile.loggingId + "\n";
		kvp += "currentProfile.netflixAccessToken=" + virtualState.currentProfile.netflixAccessToken + "\n";
		kvp += "info.sdkVersion=" + virtualSdkVersion + "\n";
		kvp += "info.projectId=" + virtualCrashReporterConfig.projectId + "\n";
		kvp += "info.crashGuid=" + virtualCrashReporterConfig.crashGuid + "\n";
		kvp += "info.description=" + virtualDescription + "\n";
		writeStringTo(STATE_FILENAME, kvp);
	}
	
	private void setStateProperty(String name, String value) {
		/* it is assumed that both strings are not null, name is not empty, but value can be empty */
		
		if (name.equals("kvp.version")) {
			int kvpVer = 0;
			try {
				kvpVer = Integer.parseInt(value);
			} catch (Exception exception) {
				Log.e(TAG, "setStateProperty: Unable to parse the KeyValuePair config version, expect bugs!", exception);
			}
			
			if (kvpVer > CURRENT_KVP_VERSION) {
				/* uh oh */
				Log.i(TAG, "setStateProperty: KeyValuePair config version is newer than what I support!!");
				Log.i(TAG, "setStateProperty: What I support = " + Integer.toString(CURRENT_KVP_VERSION) + ", and got = " + Integer.toString(kvpVer));
			}
		} else if (name.equals("currentProfile.gamerProfileId")) {
			virtualState.currentProfile.gamerProfileId = value;
		} else if (name.equals("currentProfile.locale.country")) {
			virtualState.currentProfile.locale.country = value;
		} else if (name.equals("currentProfile.locale.language")) {
			virtualState.currentProfile.locale.language = value;
		} else if (name.equals("currentProfile.locale.variant")) {
			virtualState.currentProfile.locale.variant = value;
		} else if (name.equals("currentProfile.loggingId")) {
			virtualState.currentProfile.loggingId = value;
		} else if (name.equals("currentProfile.netflixAccessToken")) {
			virtualState.currentProfile.netflixAccessToken = value;
		} else if (name.equals("info.sdkVersion")) {
			virtualSdkVersion = value;
		} else if (name.equals("info.projectId")) {
			/* the object is immutable so have this I guess */
			virtualCrashReporterConfig = new CrashReporterConfig(value, virtualCrashReporterConfig.crashGuid);
		} else if (name.equals("info.crashGuid")) {
			virtualCrashReporterConfig = new CrashReporterConfig(virtualCrashReporterConfig.projectId, value);
		} else if (name.equals("info.description")) {
			virtualDescription = value;
		} else {
			Log.i(TAG, "setStateProperty: Unknown KVP property " + name + "=" + value);
		}
	}
	
	private void readSdkStateOrInitDefault() {
		doShowUi = false;
		doHideUi = false;
		doStateChange = false;

		try {
			/* BUG BUG BUG BUG */
			/*
				TMNT has a major bug where it crashes if the auth events are processed in the checkUserAuth function.
				So we must have a separate thread to do all the work for this...
			*/
			dispatcher = new HandlerThread("NfxToUiDispatcherThread");
			dispatcher.start();
			bgLooper = dispatcher.getLooper();
			bgHandler = new Handler(bgLooper);
		} catch (Exception exception) {
			exception.printStackTrace();
		}
		
		String kvp = readStringFrom(STATE_FILENAME);
		/* previous profile is always null since we simulate a single user environment */
		NetflixProfile defaultProfile = new NetflixProfile();
		defaultProfile.locale = new Locale();
		virtualCrashReporterConfig = new CrashReporterConfig(DEFAULT_CRASH_PROJECT_ID, DEFAULT_CRASH_GUID);
		/* default profile info goes here */
		defaultProfile.gamerProfileId = DEFAULT_PROFILE_ID;
		defaultProfile.locale.country = DEFAULT_COUNTRY; /* this works 100% of the time */
		defaultProfile.locale.language = DEFAULT_LANGUAGE;
		defaultProfile.locale.variant = DEFAULT_VARIANT;
		defaultProfile.loggingId = DEFAULT_LOGGING_ID;
		defaultProfile.netflixAccessToken = DEFAULT_TOKEN;
		virtualSdkVersion = DEFAULT_SDK_VERSION;
		virtualDescription = DEFAULT_DESCRIPTION;
		virtualState = new NetflixSdkState(defaultProfile, null);
		
		if (kvp.length() <= 0) {
			Log.i(TAG, "readSdkStateOrInitDefault: default state");
			/* write it */
			writeSdkState(); /* populate it in storage */
			return;
		}
		
		/* actually load then... */
		String[] kvplines = kvp.split("\n");
		for (int i = 0, n = kvplines.length; i < n; ++i) {
			String kvpline = kvplines[i];
			/* a line must have at least one character, the equals sign */
			if (kvpline == null || kvpline.length() < 2) {
				continue;
			}
			
			int idx = kvpline.indexOf("=");
			/* a line cannot start with an equals sign */
			if (idx <= 0) {
				continue;
			}
			
			String propertyName = kvpline.substring(0, idx);
			String propertyValue = kvpline.substring(idx + 1);
			Log.i(TAG, "readSdkStateOrInitDefault: read property " + propertyName + "=" + propertyValue);
			setStateProperty(propertyName, propertyValue);
		}
		
		Log.i(TAG, "readSdkStateOrInitDefault: state is applied");
	}
	/* -- private methods end -- */
	
	/* -- constructors begin -- */
	public NetflixGameSdk(Context sdkAppContext) {
		/* must be a valid Android application context, i.e. never null */
		applicationContext = sdkAppContext;
		if (applicationContext == null) {
			throw new RuntimeException("applicationContext can't be null");
		}
		
		virtualDirectory = applicationContext.getExternalFilesDir((String)null);
		utf8Charset = Charset.forName("UTF-8");
		/* initialize a default user state, or load a custom one if present */
		readSdkStateOrInitDefault();
	}
	/* -- constructors end -- */
	
	/* -- FilenameFilter implementation begin -- */
	@Override
	public boolean accept(File dir, String name) {
		/* this is only for save slots enumeration, filter AAAA.slot files */
		return name != null && name.endsWith(".slot");
	}
	/* -- FilenameFilter implementation end -- */
	
	/* -- CloudSave implementation begin -- */
	@Override
	public void deleteSlot(String slotId, DeleteSlotCallback onDeleteSlot) {
		if (slotId == null || slotId.length() <= 0) {
			Log.i(TAG, "deleteSlot: slotId is null");
			return;
		}
		
		Log.i(TAG, "deleteSlot: " + slotId);
		deleteFile(slotId + ".slot");
		if (onDeleteSlot != null) {
			/* dummy slotinfos since we pretend it's gone from both cloud and local */
			/* (obviously cloud === local in this context) */
			CloudSave.DeleteSlotResult result = new CloudSave.DeleteSlotResult();
			result.status = CloudSaveStatus.OK;
			result.conflictResolution = null;
			onDeleteSlot.onResult(result);
		}
	}
	
	@Override
	public void getSlotIds(GetSlotIdsCallback onGetSlotIds) {
		if (onGetSlotIds == null) {
			Log.i(TAG, "getSlotIds: onGetSlotIds is null");
			return;
		}
		
		ArrayList<String> listOfIds = new ArrayList<String>();
		File[] files = virtualDirectory.listFiles((FilenameFilter) this);
		if (files != null) {
			for (int i = 0, n = files.length; i < n; ++i) {
				String fileName = files[i].getName();
				String slotId = fileName.substring(0, fileName.lastIndexOf('.'));
				Log.i(TAG, "getSlotIds: adding save slot id " + slotId);
				listOfIds.add(slotId);
			}
		} else {
			Log.i(TAG, "getSlotIds: listFiles returned null");
		}
		CloudSave.GetSlotIdsResult result = new CloudSave.GetSlotIdsResult();
		result.status = CloudSaveStatus.OK;
		result.slotIds = (List<String>) listOfIds;
		onGetSlotIds.onResult(result);
	}
	
	@Override
	public void readSlot(String slotId, ReadSlotCallback onReadSlot) {
		if (slotId == null || slotId.length() <= 0) {
			Log.i(TAG, "readSlot: slot id is null");
			return;
		}
		
		if (onReadSlot == null) {
			Log.i(TAG, "readSlot: onReadSlot is null");
			return;
		}
		
		Log.i(TAG, "readSlot: reading data from " + slotId);
		byte[] rawData = readAllBytesFrom(slotId + ".slot");
		/* this returns null if we have no data, which is what we want */
		CloudSave.ReadSlotResult result = new CloudSave.ReadSlotResult();
		if (rawData == null) {
			/* we do not know this slot yet */
			result.status = CloudSaveStatus.ERROR_UNKNOWN_SLOT_ID;
			result.slotInfo = null;
		}
		else {
			result.status = CloudSaveStatus.OK;
			result.slotInfo = new SlotInfo(rawData);
		}
		result.conflictResolution = null; /* we cannot have conflicts when local is always cloud */
		onReadSlot.onResult(result);
	}
	
	@Override
	public void resolveConflict(String slotId, CloudSaveResolution cloudSaveResolution, ResolveConflictCallback onResolveConflict) {
		if (slotId == null || slotId.length() <= 0) {
			Log.i(TAG, "resolveConflict: slotId is null or empty");
			return;
		}
		
		Log.i(TAG, "resolveConflict: " + slotId + "," + cloudSaveResolution.toString());
		if (onResolveConflict != null) {
			/* we are never supposed to have conflicts, lol, this is just impossible */
			CloudSave.ResolveConflictResult result = new CloudSave.ResolveConflictResult();
			result.status = CloudSaveStatus.OK;
			onResolveConflict.onResult(result);
		}
	}
	
	@Override
	public void saveSlot(String slotId, SlotInfo slotInfo, SaveSlotCallback onSaveSlot) {
		if (slotId == null || slotId.length() <= 0) {
			Log.i(TAG, "saveSlot: slotId is null or empty");
			return;
		}
		
		if (slotInfo == null) {
			Log.i(TAG, "saveSlot: slot info is null");
			return;
		}
		
		Log.i(TAG, "saveSlot: saving to " + slotId);
		
		byte[] data = slotInfo.getDataBytes();
		if (data == null) {
			Log.i(TAG, "saveSlot: byte data is null?????");
			/* uhhh delete the file then??? */
			deleteFile(slotId + ".slot");
		}
		else {
			writeAllBytesTo(slotId + ".slot", data);
		}
		
		if (onSaveSlot != null) {
			CloudSave.SaveSlotResult result = new CloudSave.SaveSlotResult();
			result.status = CloudSaveStatus.OK;
			result.conflictResolution = null; /* we cannot have conflicts in a simulation */
			onSaveSlot.onResult(result);
		}
	}
	
	@Override
	public CloudSave getCloudSave() {
		return (CloudSave) this;
	}
	/* -- CloudSave implementation end -- */
	
	/* -- Leaderboards implementation begin -- */
	@Override
	public void getCurrentPlayerEntry(String boardId, LeaderboardEntryCallback onLeaderboardEntry) {
		if (boardId == null || boardId.length() <= 0) {
			Log.i(TAG, "getCurrentPlayerEntry: board id is null or empty");
			return;
		}
		
		Log.i(TAG, "getCurrentPlayerEntry: " + boardId);
		if (onLeaderboardEntry != null) {
			Leaderboards.LeaderboardEntryResult result = new Leaderboards.LeaderboardEntryResult();
			result.status = LeaderboardStatus.ERROR_ENTRY_NOT_FOUND;
			result.leaderboardEntry = null;
			onLeaderboardEntry.onResult(result);
		}
	}
	
	@Override
	public void getEntriesAroundCurrentPlayer(String boardId, int maxEntries, LeaderboardEntriesCallback onLeaderboardEntries) {
		if (boardId == null || boardId.length() <= 0) {
			Log.i(TAG, "getEntriesAroundCurrentPlayer: board id is null or empty");
			return;
		}
		
		Log.i(TAG, "getEntriesAroundCurrentPlayer: " + boardId);
		if (onLeaderboardEntries != null) {
			Leaderboards.LeaderboardEntriesResult result = new Leaderboards.LeaderboardEntriesResult();
			result.status = LeaderboardStatus.ERROR_ENTRY_NOT_FOUND;
			result.page = null;
			onLeaderboardEntries.onResult(result);
		}
	}
	
	@Override
	public void getLeaderboardInfo(String boardId, LeaderboardInfoCallback onLeaderboardInfo) {
		if (boardId == null || boardId.length() <= 0) {
			Log.i(TAG, "getLeaderboardInfo: board id is null or empty");
			return;
		}
		
		Log.i(TAG, "getLeaderboardInfo: " + boardId);
		if (onLeaderboardInfo != null) {
			Leaderboards.LeaderboardInfoResult result = new Leaderboards.LeaderboardInfoResult();
			result.status = LeaderboardStatus.ERROR_ENTRY_NOT_FOUND;
			result.info = null;
			onLeaderboardInfo.onResult(result);
		}
	}
	
	@Override
	public void getMoreEntries(String boardId, String unk, int maxEntries, FetchDirection fetchDirection, LeaderboardEntriesCallback onLeaderboardEntries) {
		if (boardId == null || boardId.length() <= 0) {
			Log.i(TAG, "getMoreEntries: board id is null or empty");
			return;
		}
		
		Log.i(TAG, "getMoreEntries: " + boardId);
		if (onLeaderboardEntries != null) {
			Leaderboards.LeaderboardEntriesResult result = new Leaderboards.LeaderboardEntriesResult();
			result.status = LeaderboardStatus.ERROR_ENTRY_NOT_FOUND;
			result.page = null;
			onLeaderboardEntries.onResult(result);
		}
	}
	
	@Override
	public void getTopEntries(String boardId, int maxEntries, LeaderboardEntriesCallback onLeaderboardEntries) {
		if (boardId == null || boardId.length() <= 0) {
			Log.i(TAG, "getTopEntries: board id is null or empty");
			return;
		}
		
		Log.i(TAG, "getTopEntries: " + boardId);
		if (onLeaderboardEntries != null) {
			Leaderboards.LeaderboardEntriesResult result = new Leaderboards.LeaderboardEntriesResult();
			result.status = LeaderboardStatus.ERROR_ENTRY_NOT_FOUND;
			result.page = null;
			onLeaderboardEntries.onResult(result);
		}
	}
	
	@Override
	public Leaderboards getLeaderboards() {
		return (Leaderboards) this;
	}
	/* -- Leaderboards implementation end -- */
	
	/* -- NetflixMessaging implementation begin -- */
	@Override
	public void onDeeplinkReceived(String deeplinkId, boolean flag) {
		if (deeplinkId == null) {
			Log.i(TAG, "onDeeplinkReceived: deeplinkId is null");
		} else {
			Log.i(TAG, "onDeeplinkReceived: " + deeplinkId + "," + Boolean.toString(flag));
		}
	}
	
	@Override
	public void onMessagingEvent(MessagingEventType messagingEventType, String str) {
		if (str == null) {
			str = "<null>";
		}
		
		Log.i(TAG, "onMessagingEvent: " + messagingEventType.toString() + "," + str);
	}
	
	@Override
	public void onPushToken(String pushToken) {
		if (pushToken == null) {
			Log.i(TAG, "onPushToken: pushToken is null");
		} else {
			Log.i(TAG, "onPushToken: " + pushToken);
		}
	}
	
	@Override
	public NetflixMessaging getNetflixMessaging() {
		return (NetflixMessaging) this;
	}
	/* -- NetflixMessaging implementation end -- */
	
	/* -- PlayerIdentity implementation begin -- */
	
	@Override
	public String getHandle() {
		Log.i(TAG, "getHandle: returning current player token");
		/* TMNT calls this and draws this as the player's name apparently??? */
		return virtualState.currentProfile.netflixAccessToken;
	}
	
	@Override
	public String getPlayerId() {
		Log.i(TAG, "getPlayerId: returning current player profile id");
		return virtualState.currentProfile.gamerProfileId;
	}
	/* -- PlayerIdentity implementation end -- */
	
	/* -- NetflixPlayerIdentity implementation begin -- */
	@Override
	public PlayerIdentity getCurrentPlayer() {
		Log.i(TAG, "getCurrentPlayer: returning a virtual player");
		return (PlayerIdentity) this;
	}
	
	@Override
	public void getPlayerIdentities(List<String> listOfPlayerIds, GetPlayerIdentitiesCallback onGetPlayerIdentities) {
		if (onGetPlayerIdentities == null) {
			Log.i(TAG, "getPlayerIdentities: onGetPlayerIdentities is null");
			return;
		}
		
		Log.i(TAG, "getPlayerIdentities: returning faux player identities");
		HashMap<String, PlayerIdentityResult> playerMap = new HashMap<String, PlayerIdentityResult>();
		if (listOfPlayerIds != null) {
			for (int i = 0, n = listOfPlayerIds.size(); i < n; ++i) {
				String item = listOfPlayerIds.get(i);
				
				NetflixPlayerIdentity.PlayerIdentityResult identityResult = new NetflixPlayerIdentity.PlayerIdentityResult();
				if (item != null && (item == getHandle() || item == getPlayerId())) {
					identityResult.status = PlayerIdentityStatus.OK;
					identityResult.playerIdentity = getCurrentPlayer();
				} else {
					identityResult.status = PlayerIdentityStatus.NOT_FOUND;
					identityResult.playerIdentity = null;
				}
				
				playerMap.put(item, identityResult);
			}
		}
		
		NetflixPlayerIdentity.GetPlayerIdentitiesResult result = new NetflixPlayerIdentity.GetPlayerIdentitiesResult();
		result.description = virtualDescription;
		result.identities = playerMap;
		result.status = RequestStatus.OK;
		onGetPlayerIdentities.onGetPlayerIdentities(result);
	}
	
	@Override
	public NetflixPlayerIdentity getNetflixPlayerIdentity() {
		return (NetflixPlayerIdentity) this;
	}
	/* -- NetflixPlayerIdentity implementation end -- */
	
	/* -- NetflixSdk implementation begin -- */
	@Override
	public void run() {
		if (Looper.myLooper() != bgLooper) {
			Log.i(TAG, "run: in a UI thread");
			
			if (doShowUi) {
				doShowUi = false;
				throwNetflixUiVisible();
			}
			
			if (doHideUi) {
				doHideUi = false;
				throwNetflixUiHidden();
			}
			
			if (doStateChange) {
				doStateChange = false;
				throwUserStateChange();
			}
			
			return;
		}
		
		Log.i(TAG, "run: in a custom thread, passing to activity");
		lastActivity.runOnUiThread((Runnable) this);
	}
	
	@Override
	public void checkUserAuth(Activity activity) {
		Log.i(TAG, "checkUserAuth: requested a user state change");
		if (activity == null) {
			Log.i(TAG, "checkUserAuth: activity is null");
			return;
		}
		
		Log.i(TAG, "checkUserAuth: dispatching a UI event");
		doStateChange = true;
		lastActivity = activity;
		bgHandler.postDelayed((Runnable) this, 1000L);
		Log.i(TAG, "checkUserAuth: placed");
	}
	
	@Override
	public CrashReporterConfig getCrashReporterConfig() {
		Log.i(TAG, "getCrashReporterConfig: returning dummy config");
		return virtualCrashReporterConfig;
	}
	
	@Override
	public String getSdkVersion() {
		Log.i(TAG, "getSdkVersion: returning dummy version");
		return virtualSdkVersion;
	}
	
	@Override
	public void hideNetflixAccessButton(Activity activity) {
		Log.i(TAG, "hideNetflixAccessButton: hiding");
		if (activity == null) {
			Log.i(TAG, "hideNetflixAccessButton: activity is null");
			return;
		}
		
		doHideUi = true;
		lastActivity = activity;
		bgHandler.postDelayed((Runnable) this, 1000L);
	}
	
	@Override
	@Deprecated
	public void hideNetflixMenu(Activity activity) {
		Log.i(TAG, "hideNetflixMenu: requested ui hide");
		if (activity == null) {
			Log.i(TAG, "hideNetflixMenu: activity is null");
			return;
		}
		
		doHideUi = true;
		lastActivity = activity;
		bgHandler.postDelayed((Runnable) this, 1000L);
	}
	
	@Override
	public void leaveBreadcrumb(String breadcrumb) {
		if (breadcrumb == null) {
			breadcrumb = "<null>";
		}
		
		Log.i(TAG, "leaveBreadcrumb: " + breadcrumb);
	}
	
	@Override
	public void logHandledException(Throwable throwable) {
		if (throwable == null) {
			Log.e(TAG, "logHandledException: null exception", null);
		}
		else {
			Log.e(TAG, "logHandledException: ", throwable);
		}
	}
	
	@Override
	public void sendCLEvent(String unk, String unk2) {
		if (unk == null) {
			unk = "<null>";
		}
		
		if (unk2 == null) {
			unk2 = "<null>";
		}
		
		Log.i(TAG, "sendCLEvent: " + unk + " / " + unk2);
	}
	
	@Override
	public void setLocale(Locale locale) {
		if (locale == null) {
			Log.i(TAG, "setLocale: locale is null");
			return;
		}
		
		virtualState.currentProfile.locale.country = locale.country;
		virtualState.currentProfile.locale.language = locale.language;
		virtualState.currentProfile.locale.variant = locale.variant;
		Log.i(TAG, "setLocale: " + locale.country + "," + locale.language + "," + locale.variant);
		writeSdkState();
	}
	
	@Override
	public void showNetflixAccessButton(Activity activity) {
		Log.i(TAG, "showNetflixAccessButton: show netflix btn");
		if (activity == null) {
			Log.i(TAG, "showNetflixAccessButton: activity is null");
			return;
		}
	}
	
	@Override
	@Deprecated
	public void showNetflixMenu(Activity activity, int idx) {
		Log.i(TAG, "showNetflixMenu: " + Integer.toString(idx));
		if (activity == null) {
			Log.i(TAG, "showNetflixMenu: activity is null");
			return;
		}
	}
	
	@Override
	public NetflixSdk getNetflixSdk() {
		return (NetflixSdk) this;
	}
	/* -- NetflixSdk implementation end -- */
	
	/* -- Stats implementation begin -- */
	private long parseLongZero(String stringLong) {
		/* return zero since this is the default value for missing stats */
		long longValue = 0L;
		
		if (stringLong != null && stringLong.length() > 0) {
			try {
				longValue = Long.parseLong(stringLong);
			} catch (Exception exception) {
				/* oh well */
				exception.printStackTrace();
			}
		}
		
		return longValue;
	}
	
	@Override
	public void getAggregatedStat(String statId, Stats.AggregatedStatCallback onAggregatedStat) {
		if (statId == null || statId.length() <= 0) {
			Log.i(TAG, "getAggregatedStat: null or empty name");
			return;
		}
		
		if (onAggregatedStat != null) {
			long statValue = 0;
			String statValueString = readStringFrom(statId + ".stat");
			if (statValueString.length() > 0) {
				statValue = parseLongZero(statValueString);
			}
			
			Log.i(TAG, "getAggregatedStat: " + statId + " = " + statValueString);
			Stats.AggregatedStatResult result = new Stats.AggregatedStatResult();
			result.aggregatedStat = new AggregatedStat();
			result.aggregatedStat.name = statId;
			result.aggregatedStat.value = statValue;
			result.status = StatsStatus.OK;
			onAggregatedStat.onResult(result);
		}
	}
	
	@Override
	public void submitStat(StatItem statItem) {
		if (statItem == null || statItem.name == null || statItem.name.length() <= 0) {
			Log.i(TAG, "submitStat: null stat or name");
			return;
		}
		
		String previousValue = readStringFrom(statItem.name + ".stat");
		if (previousValue.length() > 0) {
			Log.i(TAG, "submitStat: this stat already exists, adding");
			previousValue = Long.toString(parseLongZero(previousValue) + statItem.value);
		}
		else {
			Log.i(TAG, "submitStat: this stat is being created just now");
			previousValue = Long.toString(statItem.value);
		}
		
		String valueString = previousValue;
		Log.i(TAG, "submitStat: " + statItem.name + " = " + valueString);
		writeStringTo(statItem.name + ".stat", valueString);
	}
	
	@Override
	public void submitStatNow(StatItem statItem, Stats.SubmitStatCallback onSubmitStat) {
		if (statItem == null || statItem.name == null || statItem.name.length() <= 0) {
			Log.i(TAG, "submitStatNow: null stat or name");
			return;
		}
		
		submitStat(statItem);
		if (onSubmitStat != null) {
			String currentValueString = readStringFrom(statItem.name + ".stat");
			long currentValue = statItem.value;
			if (currentValueString.length() > 0) {
				currentValue = parseLongZero(currentValueString);
			}
			
			Log.i(TAG, "submitStatNow: " + statItem.name + " = " + currentValueString);
			Stats.SubmitStatResult result = new Stats.SubmitStatResult();
			result.aggregatedStat = new AggregatedStat();
			result.aggregatedStat.name = statItem.name;
			result.aggregatedStat.value = currentValue;
			result.status = StatsStatus.OK;
			result.submittedStat = statItem;
			onSubmitStat.onResult(result);
		}
	}
	
	@Override
	public Stats getStats() {
		return (Stats) this;
	}
	/* -- Stats implementation end -- */
	
	@Override
	public void registerEventReceiver(NetflixSdkEventHandler newEventHandler) {
		if (newEventHandler == null) {
			Log.i(TAG, "registerEventReceiver: <null>");
		} else {
			Log.i(TAG, "registerEventReceiver: handler is not null");
		}
		
		eventHandler = newEventHandler;
	}
}
/* -- class NetflixGameSdk end -- */

/* EOF */
