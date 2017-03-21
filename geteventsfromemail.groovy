import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMultipart;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.collections.list.TreeList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.Calendar.Events.Update;
import com.google.api.services.calendar.CalendarRequest;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

public class GetEventsFromEmail {

	private static final String CONFIG_FOLDER = System.getProperty("user.home") + "/.gcal_task_warrior";
	private static final String TASKS_FILE = CONFIG_FOLDER + "/tasks.json";
	private static final String TAGS_FILE = CONFIG_FOLDER + "/tags.json";
	private static final String CLIENT_SECRETS = System.getProperty("user.home") + "/github/google_task_warrior/client_secrets.json";

	public static void main(String[] args) throws URISyntaxException, NoSuchProviderException,
			MessagingException, IOException {
		if (!Paths.get(CLIENT_SECRETS).toFile().exists()) {
			throw new RuntimeException("Make sure ~/client_secrets.json exists");
		}
		getErrandsInSeparateThread();
	}

	private static void getErrandsInSeparateThread() {
		try {
			ListDisplaySynchronous.getErrands(TASKS_FILE);
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
		} catch (MessagingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Path("not_now")
	public static class HelloWorldResource { // Must be public
		private static final String string = "/home/sarnobat/.gcal_task_warrior";
		private static final File file = new File(string + "/tasks.json");

		@GET
		@Path("items")
		@Produces("application/json")
		public Response listItems(@QueryParam("rootId") Integer iRootId) throws Exception {
			try {
				JSONObject json = new JSONObject();
				json.put("tasks", ListDisplaySynchronous.getErrandsJsonFromEmail(TASKS_FILE));
				if (Paths.get(TAGS_FILE).toFile().exists()) {
					json.put("tags",
							Tags.getTasksWithTags(Paths.get(TAGS_FILE), Paths.get(TASKS_FILE)));
				}
				FileUtils.writeStringToFile(file, json.toString(2));
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(json.toString()).type("application/json").build();
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		}

		private static class Tags {

			public static JSONObject getTasksWithTags(java.nio.file.Path tagsFile,
					java.nio.file.Path tasksFile) {
				JSONObject syncWithLatestTasksFile = getFilteredTaggedTasks(
						readFileToJson(tagsFile), tasksFile);
				// I think this is a legitimate exception to the rule where you
				// should
				// make methods side-effect free if they return something.
				// Write it back out to the tags file
				return syncWithLatestTasksFile;
			}

			private static JSONObject readFileToJson(java.nio.file.Path tagsFile) {
				String tagsObject;
				try {
					tagsObject = FileUtils.readFileToString(tagsFile.toFile());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				return new JSONObject(tagsObject);
			}

			private static JSONObject getFilteredTaggedTasks(JSONObject taggedTasks,
					java.nio.file.Path tasksFile) {
				Set<String> currentTaskTitles = getCurrentTasks(tasksFile);
				// System.out.println("NotNow.HelloWorldResource.Tags.getFilteredTaggedTasks() - "
				// + currentTaskTitles);
				return filterTaggedTasks(taggedTasks, currentTaskTitles);
			}

			private static JSONObject filterTaggedTasks(JSONObject taggedTasks,
					Set<String> currentTaskTitles) {
				JSONObject ret = new JSONObject();
				for (String key : taggedTasks.keySet()) {
					JSONArray arr = taggedTasks.getJSONArray(key);
					JSONArray filteredArray = new JSONArray();
					for (int i = 0; i < arr.length(); i++) {
						String taskTitle = arr.getString(i);
						if (currentTaskTitles.contains(taskTitle)) {
							filteredArray.put(taskTitle);
						}
					}
					ret.put(key, filteredArray);
				}
				return ret;
			}

			private static Set<String> getCurrentTasks(java.nio.file.Path tasksFile) {
				JSONObject currentTasks = readFileToJson(tasksFile).getJSONObject("tasks");
				Set<String> currentTaskTitles = getTaskTitlesFromTasksObject(currentTasks);
				return currentTaskTitles;
			}

			private static Set<String> getTaskTitlesFromTasksObject(JSONObject currentTasks) {
				// System.out.println("NotNow.HelloWorldResource.Tags.getTaskTitlesFromTasksObject() - "
				// + currentTasks);
				Set<String> keys = FluentIterable.from(currentTasks.keySet()).filter(TASK_KEY)
						.toSet();
				ImmutableSet.Builder<String> titles = ImmutableSet.builder();
				for (String key : keys) {
					JSONObject task = currentTasks.getJSONObject(key);
					// System.out.println("NotNow.HelloWorldResource.Tags.getTaskTitlesFromTasksObject() - "
					// + task);
					// System.out.println("NotNow.HelloWorldResource.Tags.getTaskTitlesFromTasksObject() - key = "
					// + key);
					// System.out.println("NotNow.HelloWorldResource.Tags.getTaskTitlesFromTasksObject() - "
					// + task.toString());
					titles.add(task.getString("title"));
				}
				return titles.build();
			}

			private static final Predicate<String> TASK_KEY = new Predicate<String>() {
				@Override
				public boolean apply(String input) {
					return !input.equals("daysToPostpone");
				}
			};

			public static void addTag(Integer iItemNumber, String iTagName, String tasksFile,
					String tagsFile) {
				String taskTitle = getTaskTitle(iItemNumber, tasksFile);
				JSONObject taggedTasks = readFileToJson(Paths.get(tagsFile));
				JSONArray a = taggedTasks.getJSONArray(iTagName);
				Set<String> taskTitles = toSet(a);
				if (!taskTitles.contains(taskTitle)) {
					a.put(taskTitle);
				}
				taggedTasks.put(iTagName, a);
			}

			private static Set<String> toSet(JSONArray iArray) {
				ImmutableSet.Builder<String> ret = ImmutableSet.builder();
				for (int i = 0; i < iArray.length(); i++) {
					ret.add(iArray.getString(i));
				}
				return ret.build();
			}

			private static String getTaskTitle(Integer iItemNumber, String tasksFile) {
				JSONObject tasks = readFileToJson(Paths.get(tasksFile)).getJSONObject("tasks");
				// System.out.println("NotNow.HelloWorldResource.Tags.getTaskTitle() - item number "
				// + iItemNumber);
				// System.out.println("NotNow.HelloWorldResource.Tags.getTaskTitle() - "
				// + tasks.toString());
				return tasks.getJSONObject(iItemNumber.toString()).getString("title");
			}
		}

		@GET
		@Path("tag")
		@Produces("application/json")
		public Response tag(@QueryParam("itemNumber") Integer iItemNumber,
				@QueryParam("tag") String iTagName) throws Exception {

			try {
				Tags.addTag(iItemNumber, iTagName, TASKS_FILE, TAGS_FILE);
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(new JSONObject()).type("application/json").build();
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		}

		private static String formatTitleForPrinting(String string) {
			String[] aTitle = string.split("@");
			String repeating = "";
			if (aTitle.length > 1 && aTitle[1].contains("Repeating")) {
				repeating = "[Repeating] ";
			}
			String aTitleMain = aTitle[0].replace("Reminder: ", "").replace("Notification: ", "");
			String printedTitle = repeating + aTitleMain;

			String capitalize = StringUtils.capitalize(printedTitle);
			return capitalize;
		}

		private static JSONObject getEventJson(String itemToDelete, String errands) {
			JSONObject allErrandsJson = new JSONObject(errands);
			// System.out.println(allErrandsJson);
			JSONObject eventJson = (JSONObject) allErrandsJson.getJSONObject("tasks").get(
					itemToDelete);
			return eventJson;
		}


		/************************************************************************
		 * Boilerplate
		 ************************************************************************/

		private static Calendar getCalendarService() {
			return getCalendarService(FileUtils.getFile(CLIENT_SECRETS),
					System.getProperty("user.home") + "/.store/calendar_sample");
		}

		private static Calendar getCalendarService(HttpTransport httpTransport, File file2,
				String pathname) throws IOException, FileNotFoundException {
			return checkNotNull(getClient(httpTransport, new InputStreamReader(new FileInputStream(
					file2)), new File(pathname)));
		}

		private static Calendar getClient(HttpTransport httpTransport,
				InputStreamReader clientSecrets, java.io.File homeDir) throws IOException {
			return new Calendar.Builder(httpTransport, JacksonFactory.getDefaultInstance(),
					getAuthCode(httpTransport, clientSecrets, homeDir).authorize("user"))
					.setApplicationName("gcal-task-warrior").build();
		}

		private static AuthorizationCodeInstalledApp getAuthCode(HttpTransport httpTransport,
				InputStreamReader clientSecrets, java.io.File homeDir) throws IOException {
			return new AuthorizationCodeInstalledApp(new GoogleAuthorizationCodeFlow.Builder(
					httpTransport, JacksonFactory.getDefaultInstance(), GoogleClientSecrets.load(
							JacksonFactory.getDefaultInstance(), clientSecrets), ImmutableSet.of(
							CalendarScopes.CALENDAR, CalendarScopes.CALENDAR_READONLY))
					.setDataStoreFactory(new FileDataStoreFactory(homeDir)).build(),
					new LocalServerReceiver());
		}
	}

	private static class ListDisplaySynchronous {


		/************************************************************************
		 * Boilerplate
		 ************************************************************************/

		@Deprecated
		// Why?
		static void getErrands(String tasksFilePath) throws NoSuchProviderException,
				MessagingException, IOException {
			JSONObject json = new JSONObject();
			json.put("tasks", getErrandsJsonFromEmail(tasksFilePath));
			System.out.println("GetEventsFromEmail.ListDisplaySynchronous.getErrands()" + json.toString(2));
		}

		static JSONObject getErrandsJsonFromEmail(String tasksFilePath)
				throws NoSuchProviderException, MessagingException, IOException {
			System.out.println("getErrandsJsonFromEmail() - " + "Messages obtained");
			JSONObject json = createJsonListOfEvents(getMessages());
			json.put("daysToPostpone", getPostponeCount(tasksFilePath));
			return json;
		}

		private static int getPostponeCount(String tasksFilePath) throws IOException {
			int DAYS_TO_POSTPONE = 30;
			int daysToPostponeSaved = DAYS_TO_POSTPONE;
			if (!new File(tasksFilePath).exists()) {
				daysToPostponeSaved = DAYS_TO_POSTPONE;
			} else {
				String errands = FileUtils.readFileToString(new File(tasksFilePath));
				JSONObject allErrandsJsonOriginal = new JSONObject(errands);
				if (allErrandsJsonOriginal.has("daysToPostpone")) {
					daysToPostponeSaved = allErrandsJsonOriginal.getInt("daysToPostpone");
				} else {
					daysToPostponeSaved = DAYS_TO_POSTPONE;
				}
			}
			return daysToPostponeSaved;
		}

		private static JSONObject createJsonListOfEvents(Message[] msgs) throws MessagingException {
			Map<String, JSONObject> messages = new TreeMap<String, JSONObject>();
			// int i = 0;
			for (Message aMessage : msgs) {
				JSONObject messageMetadata = Preconditions
						.checkNotNull(getMessageMetadata(aMessage));
				String string = messageMetadata.getString("title");
				String capitalize = HelloWorldResource.formatTitleForPrinting(string);
				messages.put(capitalize, messageMetadata);
			}
			int i = 0;
			JSONObject jsonToBeSaved = new JSONObject();
			for (String aTitle : new TreeSet<String>(messages.keySet())) {
				++i;
				JSONObject messageMetadata = messages.get(aTitle);
				// System.out.println(i + "\t" + aTitle);
				jsonToBeSaved.put(Integer.toString(i), messageMetadata);
			}
			return jsonToBeSaved;
		}

		private static JSONObject getMessageMetadata(Message aMessage) {
			JSONObject errandJsonObject;
			try {
				errandJsonObject = new JSONObject();
				String title;
				// Leave this as-s for writing. Only when displaying should you
				// abbreviate
				title = aMessage.getSubject();

				errandJsonObject.put("title", title);
				return errandJsonObject;
			} catch (MessagingException e) {
				e.printStackTrace();
			}
			return null;
		}

		private static Message[] getMessages() throws NoSuchProviderException, MessagingException {
			// System.out.println("Connecting");
			Store theImapClient = connect();
			Folder folder = theImapClient.getFolder("3 - Urg - time sensitive");
			// System.out.println("Opening");
			folder.open(Folder.READ_ONLY);

			// System.out.println("Getting Message list");
			Message[] msgs = folder.getMessages();

			FetchProfile fp = new FetchProfile();
			fp.add(FetchProfile.Item.ENVELOPE);
			// System.out.print("getMessages() - Fetching message attributes...");
			folder.fetch(msgs, fp);
			// System.out.println("done");
			theImapClient.close();
			return msgs;
		}

		private static Store connect() throws NoSuchProviderException, MessagingException {
			Properties props = System.getProperties();
			String password = System.getenv("GMAIL_PASSWORD");
			if (password == null) {
				throw new RuntimeException(
						"Please specify your password by running export GMAIL_PASSWORD=mypassword groovy mail.groovy");
			}
			props.setProperty("mail.store.protocol", "imap");
			Store theImapClient = Session.getInstance(props).getStore("imaps");
			theImapClient.connect("imap.gmail.com", "sarnobat.hotmail@gmail.com", password);
			return theImapClient;
		}
	}
}
