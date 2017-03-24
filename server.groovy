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

import javax.mail.BodyPart;
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
import javax.mail.internet.MimePartDataSource;
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

public class NotNow {

	private static final String CONFIG_FOLDER = "/home/sarnobat/.gcal_task_warrior";
	private static final String TASKS_FILE = CONFIG_FOLDER + "/tasks.json";
	private static final String TAGS_FILE = CONFIG_FOLDER + "/tags.json";
	private static final String CLIENT_SECRETS = home() + "/client_secrets.json";

	private static String home() {
		return System.getProperty("user.home");
	}

	public static void main(String[] args) throws URISyntaxException, NoSuchProviderException,
			MessagingException, IOException {
		if (!Paths.get(CLIENT_SECRETS).toFile().exists()) {
			throw new RuntimeException("Make sure ~/client_secrets.json exists");
		}

		getErrandsInSeparateThread();
		ListDisplaySynchronous.writeCalendarsToFileInSeparateThread(
				"/home/sarnobat/.gcal_task_warrior", "/home/sarnobat/.gcal_task_warrior"
						+ "/calendars.json");
		try {
			JdkHttpServerFactory.createHttpServer(new URI("http://localhost:4456/"),
					new ResourceConfig(HelloWorldResource.class));
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.err.println("Port in use. Not starting new instance.");
		}
	}

	// Do not change the file without updating the UI. You'll end up deleting
	// the wrong errand (because we use event position). We can start using this
	// approach again when we use event IDs instead.
	@Deprecated
	private static void getErrandsInSeparateThread() {
		new Thread() {
			public void run() {
				try {
					JSONObject json = new JSONObject();
					json.put("tasks", ListDisplaySynchronous.getErrandsJsonFromFile(TASKS_FILE));
					FileUtils.writeStringToFile(new File(TASKS_FILE), json.toString(2));
				} catch (NoSuchProviderException e) {
					e.printStackTrace();
				} catch (MessagingException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}.start();
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
				json.put("tasks", ListDisplaySynchronous.getErrandsJsonFromFile(TASKS_FILE));
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
				try {
					FileUtils.write(tagsFile.toFile(), syncWithLatestTasksFile.toString());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
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
				Set<String> keys = FluentIterable.from(currentTasks.keySet()).filter(TASK_KEY)
						.toSet();
				ImmutableSet.Builder<String> titles = ImmutableSet.builder();
				for (String key : keys) {
					JSONObject task = currentTasks.getJSONObject(key);
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
				try {
					FileUtils.write(Paths.get(tagsFile).toFile(), taggedTasks.toString(2));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
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

		@GET
		@Path("delete")
		@Produces("application/json")
		public Response delete(@QueryParam("itemNumber") Integer iItemNumber) throws Exception {

			try {
				writeToFile(iItemNumber, DONE_FILE);
				JSONObject json = new JSONObject();
				Delete.delete(iItemNumber.toString());
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(json.toString()).type("application/json").build();
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		}

		private static final String DONE_FILE = "/home/sarnobat/sarnobat.git/mwk/errands_done.mwk";
		private static final String ARCHIVE_FILE = "/home/sarnobat/sarnobat.git/mwk/errands.mwk";

		@GET
		@Path("offload")
		@Produces("application/json")
		public Response writeToDiskAndDelete(@QueryParam("itemNumber") Integer iItemNumber)
				throws Exception {
			System.out.println("writeToDiskAndDelete() - begin");
			try {
				writeToFile(iItemNumber, "/home/sarnobat/sarnobat.git/www/errands/all.txt");
				System.out.println("writeToDiskAndDelete() - written to file");
				Delete.delete(iItemNumber.toString());
				System.out.println("writeToDiskAndDelete() - deleted");
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(new JSONObject().toString()).type("application/json").build();
			} catch (Exception e) {
				e.printStackTrace();
				throw e;
			}
		}

		@GET
		@Path("archive")
		@Produces("application/json")
		public Response writeToDiskAndDelete2(@QueryParam("itemNumber") Integer iItemNumber)
				throws Exception {
			System.out.println("writeToDiskAndDelete() - begin");
			try {
				writeToFile(iItemNumber, ARCHIVE_FILE);
				System.out.println("writeToDiskAndDelete() - written to file");
				Delete.delete(iItemNumber.toString());
				System.out.println("writeToDiskAndDelete() - deleted");
				return Response.ok().header("Access-Control-Allow-Origin", "*")
						.entity(new JSONObject().toString()).type("application/json").build();
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

		private void writeToFile(Integer iItemNumber, String file) throws IOException {
			JSONObject eventJson = getEventJson(iItemNumber.toString(), Paths.get(TASKS_FILE)
					.toFile());
			String title = eventJson.getString("title");
			// System.out.println("NotNow.HelloWorldResource.writeToFile() - Title:\t"
			// + title);
			FileUtils.writeStringToFile(Paths.get(file).toFile(), formatTitleForPrinting(title)
					+ "\n", true);
		}

		private static JSONObject getEventJson(String itemToDelete, String errands) {
			JSONObject allErrandsJson = new JSONObject(errands);
			// System.out.println(allErrandsJson);
			JSONObject eventJson = (JSONObject) allErrandsJson.getJSONObject("tasks").get(
					itemToDelete);
			return eventJson;
		}

		// Still useful
		private static JSONObject getEventJson(String itemToDelete, File tasksFileLastDisplayed)
				throws IOException {
			String errands = FileUtils.readFileToString(tasksFileLastDisplayed);
			JSONObject eventJson = getEventJson(itemToDelete, errands);
			return eventJson;
		}

		/************************************************************************
		 * Boilerplate
		 ************************************************************************/

		private static Calendar getCalendarService() {
			return getCalendarService(FileUtils.getFile(CLIENT_SECRETS),
					System.getProperty("user.home") + "/.store/calendar_sample");
		}

		private static Calendar getCalendarService(File file2, String pathname) {
			HttpTransport httpTransport;
			try {
				httpTransport = GoogleNetHttpTransport.newTrustedTransport();
				// " - Getting client secrets...");
				// ln -s ~/github/not_now/client_secrets.json $HOME/

			} catch (GeneralSecurityException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			try {
				return getCalendarService(httpTransport, file2, pathname);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
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

		@GET
		@Path("postpone")
		@Produces("application/json")
		public Response postpone(@QueryParam("itemNumber") Integer iItemNumber,
				@QueryParam("daysToPostpone") Integer iDaysToPostpone) throws IOException,
				NoSuchProviderException, MessagingException, GeneralSecurityException {
			try {
				Postpone.postpone(iItemNumber.toString(), iDaysToPostpone.toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
			JSONObject json = new JSONObject();
			return Response.ok().header("Access-Control-Allow-Origin", "*").entity(json.toString())
					.type("application/json").build();
		}

		@GET
		@Path("postponeToNextFree")
		@Produces("application/json")
		public Response postponeToNextFree(@QueryParam("itemNumber") Integer iItemNumber)
				throws IOException, NoSuchProviderException, MessagingException,
				GeneralSecurityException {
			System.out.println("postponeToNextFree() - begin");
			try {
				PostponeToNextFree.postponeEventToNextFreeDate(iItemNumber.toString());
				// System.out.println("postponeToNextFree() - end");
				System.out.println("------------------------------------");
			} catch (Exception e) {
				e.printStackTrace();
			}
			JSONObject json = new JSONObject();
			return Response.ok().header("Access-Control-Allow-Origin", "*").entity(json.toString())
					.type("application/json").build();
		}
	}

	private static class Delete {

		private static final String DIR_PATH = "/home/sarnobat/.gcal_task_warrior";

		private static final File mTasksFileLatest = new File(DIR_PATH + "/tasks.json");

		public static void delete(String itemToDelete) throws IOException, NoSuchProviderException,
				MessagingException {
			JSONObject eventJson = getEventJson(itemToDelete, mTasksFileLatest);
			String title = eventJson.getString("title");
			Store theImapClient = connect();
			Set<Message> msgs = getMessages(title, theImapClient);
			for (Message msg : msgs) {
				if (msg == null) {
					throw new RuntimeException("msg is null");
				}
				String messageIdToDelete = getMessageID(msg);
				commit(itemToDelete, messageIdToDelete, theImapClient);
			}
			theImapClient.close();
		}

		private static JSONObject getEventJson(String itemToDelete, String errands) {
			JSONObject allErrandsJson = new JSONObject(errands);
			JSONObject eventJson = (JSONObject) allErrandsJson.getJSONObject("tasks").get(
					itemToDelete);
			return eventJson;
		}

		// Still useful
		private static JSONObject getEventJson(String itemToDelete, File tasksFileLastDisplayed)
				throws IOException {
			String errands = FileUtils.readFileToString(tasksFileLastDisplayed);
			JSONObject eventJson = getEventJson(itemToDelete, errands);
			return eventJson;
		}

		private static Set<Message> getMessages(String title, Store theImapClient)
				throws NoSuchProviderException, MessagingException {
			Message[] msgs = getMessages(theImapClient);
			ArrayList<Message> theMsgList = new ArrayList<Message>();
			// System.out.println("Delete.getMessages() - looking for " +
			// title);
			for (Message aMsg : msgs) {
				System.out.println("1111: " + aMsg.getSubject().substring(0, 15));
				System.out.println("1111: " + title);
				if (title.startsWith(aMsg.getSubject().replace("Notification: ", "")
						.substring(0, 15))) {
					theMsgList.add(checkNotNull(aMsg));
				} else {
				}
			}
			if (theMsgList.size() == 0) {
				throw new RuntimeException();
			}
			return ImmutableSet.copyOf(theMsgList);
		}

		private static Message[] getMessages(Store theImapClient) throws MessagingException {
			Folder folder = theImapClient.getFolder("3 - Urg - time sensitive");
			folder.open(Folder.READ_WRITE);

			Message[] msgs = folder.getMessages();

			FetchProfile fp = new FetchProfile();
			fp.add(FetchProfile.Item.ENVELOPE);
			fp.add("X-mailer");
			folder.fetch(msgs, fp);
			return msgs;
		}

		private static String getMessageID(Message aMessage) throws MessagingException {
			if (aMessage == null) {
				System.out.println("aMessage is null");
			}
			Enumeration<?> allHeaders = aMessage.getAllHeaders();
			String messageID = "<not found>";
			while (allHeaders.hasMoreElements()) {
				Header e = (Header) allHeaders.nextElement();
				if (e.getName().equals("Message-ID")) {
					messageID = e.getValue();
				}
			}
			return messageID;
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

		private static void commit(String itemToDelete, final String messageIdToDelete,
				final Store theImapClient) throws NoSuchProviderException, MessagingException,
				IOException {
			// All persistent changes are done right at the end, so that any
			// exceptions can get thrown first.
			try {
				deleteEmail(messageIdToDelete, theImapClient);
				System.out.println("------------------------------------");
			} catch (NoSuchProviderException e) {
				e.printStackTrace();
			} catch (MessagingException e) {
				e.printStackTrace();
			}
		}

		private static void deleteEmail(String messageIdToDelete, Store theImapClient)
				throws NoSuchProviderException, MessagingException {
			Message[] messages;
			messages = getMessages(theImapClient);
			for (Message aMessage : messages) {
				String aMessageID = getMessageID(aMessage);
				if (aMessageID.equals(messageIdToDelete)) {
					aMessage.setFlag(Flags.Flag.DELETED, true);
					System.out.println("NotNow.Delete.deleteEmail() - Deleted email:\t"
							+ aMessage.getSubject());
					break;
				}
			}
		}
	}

	private static class ListDisplaySynchronous {
		private static class CreateJsonFromEmail {
			private static JSONObject createJsonListOfEvents(Message[] msgs)
					throws MessagingException {
				Map<String, JSONObject> messages = new TreeMap<String, JSONObject>();
				for (Message aMessage : msgs) {
					JSONObject messageMetadataJson = Preconditions
							.checkNotNull(getMessageMetadata(aMessage));
					String string = messageMetadataJson.getString("title");
					String capitalize = formatTitleForPrinting(string);
					messages.put(capitalize, messageMetadataJson);
				}
				int i = 0;
				JSONObject jsonToBeSaved = new JSONObject();
				for (String aTitle : new TreeSet<String>(messages.keySet())) {
					++i;
					JSONObject messageMetadataJson = messages.get(aTitle);
					jsonToBeSaved.put(Integer.toString(i), messageMetadataJson);
				}
				return jsonToBeSaved;
			}

			private static String formatTitleForPrinting(String string) {
				String[] aTitle = string.split("@");
				String repeating = "";
				if (aTitle.length > 1 && aTitle[1].contains("Repeating")) {
					repeating = "[Repeating] ";
				}
				String aTitleMain = aTitle[0].replace("Reminder: ", "").replace("Notification: ",
						"");
				String printedTitle = repeating + aTitleMain;

				String capitalize = StringUtils.capitalize(printedTitle);
				return capitalize;
			}

			private static JSONObject getMessageMetadata(Message aMessage) {
				JSONObject errandJsonObject;
				errandJsonObject = new JSONObject();
				// Leave this as-s for writing. Only when displaying should you
				// abbreviate
				errandJsonObject.put("title", getUntruncatedTitle(aMessage));
				return errandJsonObject;
			}

			private static String getUntruncatedTitle(Message aMessage) {
				return getBody(aMessage).toString();
			}

			@Deprecated
			// make this private
			private static Message[] getMessages() throws NoSuchProviderException,
					MessagingException {
				Store theImapClient = connect();
				Folder folder = theImapClient.getFolder("3 - Urg - time sensitive");
				folder.open(Folder.READ_ONLY);

				Message[] msgs = folder.getMessages();

				FetchProfile fp = new FetchProfile();
				fp.add(FetchProfile.Item.ENVELOPE);
				folder.fetch(msgs, fp);

				for (Message aMessage : msgs) {
					JSONObject messageMetadata = Preconditions
							.checkNotNull(getMessageMetadata(aMessage));
					String string = messageMetadata.getString("title");
					String capitalize = formatTitleForPrinting(string);
				}
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

			private static String getBody(Message aMessage) {
				String out = "";
				try {
					if (!aMessage.getFolder().isOpen()) {
						aMessage.getFolder().open(Folder.READ_ONLY);
					}
					BodyPart bodyPart = ((MimeMultipart) aMessage.getContent()).getBodyPart(0);
					if (aMessage.getContent() instanceof MimeMultipart) {
						MimeMultipart m = (MimeMultipart) aMessage.getContent();
					} else {
						System.out.println("GetEventsFromEmail.getBody() content class = "
								+ aMessage.getContent().getClass());
						System.exit(-1);
					}
					if (bodyPart.getContent() instanceof String) {
						String content = (String) bodyPart.getContent();
						if (aMessage.getFolder().isOpen()) {
							aMessage.getFolder().close(false);
						}
						return content.replaceAll("[\\s\\S]*Title:.", "")
								.replaceAll("When[\\s\\S]*", "").replaceAll("\\n", "")
								.replaceAll("\\r", "");
					} else {
						MimePartDataSource plainText = (MimePartDataSource) bodyPart.getContent();
						out += plainText.getName();
					}
					if (aMessage.getFolder().isOpen()) {
						aMessage.getFolder().close(false);
					}
				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("GetEventsFromEmail.getBody() - problem with plain text: "
							+ e.getMessage());
					return "(plain text problem) ";
				}
				try {
					MimePartDataSource richText = (MimePartDataSource) ((MimeMultipart) aMessage
							.getContent()).getBodyPart(1).getContent();
					out += richText.getName();
				} catch (Exception e) {
					System.out.println("GetEventsFromEmail.getBody() - problem with rich text");
					return "(rich text problem)";
				}
				return out;
			}
		}

		@Deprecated
		// Move this to a separate subclass
		static void writeCalendarsToFileInSeparateThread(final String configFolder,
				final String calendarCacheFile) {
			new Thread() {
				public void run() {
					writeCalendars(configFolder, calendarCacheFile);
				}
			}.start();
		}

		private static void writeCalendars(String configFolder, String calendarCacheFile) {

			JSONObject json;
			try {
				json = getCalendars();

				final File file = new File(calendarCacheFile);
				FileUtils.writeStringToFile(file, json.toString(2), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			} catch (GeneralSecurityException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private static JSONObject getCalendars() throws GeneralSecurityException, IOException,
				UnsupportedEncodingException {
			Calendar client = HelloWorldResource.getCalendarService();
			// System.out.println("NotNow.ListDisplaySynchronous.getCalendars() - Getting calendars...");
			@SuppressWarnings("unchecked")
			List<CalendarListEntry> allCalendars = (List<CalendarListEntry>) client.calendarList()
					.list().execute().get("items");
			JSONObject json = new JSONObject();

			for (CalendarListEntry aCalendar : allCalendars) {
				json.put(aCalendar.getSummary(),
						new JSONObject().put("calendar_id", aCalendar.getId()));
			}
			return json;
		}

		/************************************************************************
		 * Boilerplate
		 ************************************************************************/

		static JSONObject getErrandsJsonFromFile(String tasksFilePath)
				throws NoSuchProviderException, MessagingException, IOException {

			JSONObject json1 = CreateJsonFromEmail.createJsonListOfEvents(CreateJsonFromEmail
					.getMessages());
			System.out.println("NotNow.ListDisplaySynchronous.getErrandsJsonFromFile() "
					+ json1.toString(2));
			json1.put("daysToPostpone", getPostponeCount(tasksFilePath));
			return json1;
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
	}

	private static class Postpone {

		private static final String MESSAGE_ID = "Message-ID";

		private static final String DIR_PATH = "/home/sarnobat/.gcal_task_warrior";
		private static final File mTasksFileLatest = new File(DIR_PATH + "/tasks.json");
		private static final Calendar _service = HelloWorldResource.getCalendarService();

		static void postpone(String itemNumber, String daysToPostponeString) throws IOException,
				NoSuchProviderException, MessagingException, GeneralSecurityException {
			System.out.println("Postpone.postpone() - Will postpone event " + itemNumber + " by "
					+ daysToPostponeString + " days.");
			// System.out.println("FYI - file contents at time of postpone are: "
			// + FileUtils.readFileToString(mTasksFileLatest));
			// System.out.println("NotNow.Postpone.postpone() - Title:\n\t" +
			// title);
			Store theImapClient = connect();
			doPostpone(daysToPostponeString, getEventJsonFromResponse(itemNumber, mTasksFileLatest)
					.getString("title"), theImapClient);
			if (theImapClient.isConnected()) {
				theImapClient.close();
			}
		}

		private static void doPostpone(String daysToPostponeString, String title,
				Store theImapClient) throws NoSuchProviderException, MessagingException,
				IOException, GeneralSecurityException {
			Set<Message> msgs = getMessages(theImapClient, title);
			for (Message msg : msgs) {
				commitPostpone(theImapClient, createPostponeTask(daysToPostponeString, title, msg),
						getMessageID(msg));
			}
		}

		private static Set<Message> getMessages(Store theImapClient, String title)
				throws NoSuchProviderException, MessagingException {
			Message[] msgs = getMessages(theImapClient);
			ArrayList<Message> theMsgList = new ArrayList<Message>();
			for (Message aMsg : msgs) {
				if (aMsg.getSubject().equals(title)) {
					theMsgList.add(checkNotNull(aMsg));
				} else {
				}
			}
			if (theMsgList.size() == 0) {
				throw new RuntimeException();
			}
			return ImmutableSet.copyOf(theMsgList);
		}

		private static CalendarRequest<Event> createPostponeTask(String daysToPostponeString,
				String title, Message msg) throws GeneralSecurityException, IOException,
				MessagingException {
			CalendarRequest<Event> calendarAction;
			try {
				calendarAction = createUpdateTask(getCalendarName(msg),
						getCalendarId(getCalendarName(msg)), getEventID(msg), daysToPostponeString);
			} catch (IsRecurringEventException e) {

				calendarAction = createInsertTask(daysToPostponeString, title);
			}
			return calendarAction;
		}

		private static CalendarRequest<Event> createInsertTask(String daysToPostponeString,
				String title) throws IOException {
			CalendarRequest<Event> updateTask;
			Event event = new Event();
			event.setSummary(title);
			event.setStart(new EventDateTime());
			event.setEnd(new EventDateTime());
			int daysToPostpone = Integer.parseInt(daysToPostponeString);
			postponeEvent(daysToPostpone, event);
			updateTask = _service.events().insert("primary", event);
			return updateTask;
		}

		private static String getCalendarId(String calendarName) {

			final String string = "/home/sarnobat/.gcal_task_warrior";
			final File file = new File(string + "/calendars.json");
			String s;
			try {
				s = FileUtils.readFileToString(file, "UTF-8");
				JSONObject j = new JSONObject(s);
				// System.out.println("Calendar count: " + j.length());
				if ("Sridhar Sarnobat".equals(calendarName)) {
					calendarName = "ss401533@gmail.com";
				}
				JSONObject jsonObject = (JSONObject) j.get(calendarName);
				String id = jsonObject.getString("calendar_id");
				return id;
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}

		private static void commitPostpone(Store theImapClient,
				final CalendarRequest<Event> update, final String messageIdToDelete)
				throws NoSuchProviderException, MessagingException, IOException {
			// All persistent changes are done right at the end, so that any
			// exceptions can get thrown first.
			deleteEmail(messageIdToDelete, theImapClient);

			executeCalendarRequest(update);
		}

		private static void executeCalendarRequest(final CalendarRequest<Event> update) {
			new Thread() {
				@Override
				public void run() {
					@SuppressWarnings("unused")
					Event updatedEvent;
					try {
						updatedEvent = update.execute();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}.start();
		}

		private static void deleteEmail(final String messageIdToDelete, Store theImapClient) {
			try {
				deleteEmail(theImapClient, messageIdToDelete);
			} catch (NoSuchProviderException e) {
				e.printStackTrace();
			} catch (MessagingException e) {
				e.printStackTrace();
			}
		}

		// Still useful
		private static JSONObject getEventJsonFromResponse(String itemToDelete,
				File tasksFileLastDisplayed) throws IOException {
			String errands = FileUtils.readFileToString(tasksFileLastDisplayed);
			JSONObject eventJson = getEventJsonFromResponseHelper(itemToDelete, errands);
			return eventJson;
		}

		private static JSONObject getEventJsonFromResponseHelper(String itemToDelete, String errands) {
			JSONObject jsonObject = new JSONObject(errands);
			JSONObject allErrandsJson = jsonObject.getJSONObject("tasks");
			String allErrands = allErrandsJson.toString();
			return getEventJsonFromFile(itemToDelete, allErrands);
		}

		/** Inline this into {@link NotNow#getEventJsonFromResponseHelper} */
		@Deprecated
		private static JSONObject getEventJsonFromFile(String itemToDelete, String allErrands) {
			JSONObject eventJson = (new JSONObject(allErrands)).getJSONObject(itemToDelete);
			return eventJson;
		}

		private static void deleteEmail(Store theImapClient, String messageIdToDelete)
				throws NoSuchProviderException, MessagingException {
			Message[] messages;
			messages = getMessages(theImapClient);
			for (Message aMessage : messages) {
				String aMessageID = getMessageID(aMessage);
				if (aMessageID.equals(messageIdToDelete)) {
					// TODO: it would be better to actually move the email to
					// the trash
					aMessage.setFlag(Flags.Flag.DELETED, true);
					System.out.println("deleteEmail()" + " - Deleted email:\t"
							+ aMessage.getSubject());
					break;
				}
			}
		}

		// Useful
		private static Update createUpdateTask(String calendarName, String calendarId,
				String eventID, String daysToPostponeString) throws GeneralSecurityException,
				IOException, IsRecurringEventException {
			int daysToPostpone = Integer.parseInt(daysToPostponeString);
			// Get event's current time
			return createUpdateTask(calendarId, eventID, daysToPostpone);
		}

		private static String getEventID(Message aMessage) throws IOException, MessagingException {
			String eventID = "<none>";
			MimeMultipart s = (MimeMultipart) aMessage.getContent();
			String body = (String) s.getBodyPart(0).getContent();
			if (body.trim().length() < 1) {
				System.out.println("body is empty");
			}
			if (body.contains("eid")) {
				Pattern pattern = Pattern.compile("eid=([^&" + '$' + "\\s]*)");
				Matcher m = pattern.matcher(body);
				if (!m.find()) {
					throw new RuntimeException("eid not in string 2");
				}
				eventID = m.group(1);
			}
			return eventID;
		}

		private static Update createUpdateTask(String calendarId, String eventID, int daysToPostpone)
				throws IOException, GeneralSecurityException, IsRecurringEventException {
			Event originalEvent = getEvent(eventID, calendarId);
			if (originalEvent.getRecurrence() != null) {
				throw new RuntimeException(
						"Use optional param 'singleEvents' to break recurring events into single ones");
			}
			// I don't know why the service uses a different ID
			String internalEventId = originalEvent.getId();
			Event event = _service.events().get(calendarId, internalEventId).execute();
			postponeEvent(daysToPostpone, event);
			// System.out.println("NotNow.Postpone.createUpdateTask()" +
			// " - Internal Event ID:\t" + internalEventId);
			Update update = _service.events().update(calendarId, internalEventId, event);
			return update;
		}

		private static void postponeEvent(int daysToPostpone, Event event) {
			EventDateTime eventStartTime = event.getStart();
			// System.out.println("NotNow.Postpone.postponeEvent() - Event original start time:\t"
			// + eventStartTime);
			long newStartTime = getNewStartTime(daysToPostpone);
			eventStartTime.setDateTime(new DateTime(newStartTime));

			EventDateTime endTime = event.getEnd();
			long endTimeMillis = getNewEndDateTime(daysToPostpone);
			endTime.setDateTime(new DateTime(endTimeMillis));
		}

		private static Event getEvent(String iEventId, String iCalendarId) throws IOException,
				GeneralSecurityException, IsRecurringEventException {
			Event theTargetEvent = getNonRecurringEvent(iEventId);

			if (theTargetEvent == null) {

				com.google.api.services.calendar.model.Events allEventsList;

				String aNextPageToken = null;

				while (true) {
					allEventsList = _service.events().list(iCalendarId)
							.setPageToken(aNextPageToken).execute();
					java.util.List<Event> allEventItems = allEventsList.getItems();
					for (Event anEvent : allEventItems) {
						String anHtmlLink = anEvent.getHtmlLink();
						if (anHtmlLink != null && anHtmlLink.contains(iEventId)) {
							theTargetEvent = anEvent;
						}
					}
					aNextPageToken = allEventsList.getNextPageToken();
					if (aNextPageToken == null) {
						break;
					}
				}

				if (theTargetEvent == null) {
					throw new IsRecurringEventException(
							"Couldn't find event in service: https://www.google.com/calendar/render?eid="
									+ iEventId
									+ " . Perhaps it is a repeated event? The event ID in the email is the latest one; the html link from the service is the first in the series. I can't get instances() to return all instances in the series because I think you need to pass the first event Id, not the latest. ");
				}
			}
			return theTargetEvent;
		}

		private static Event getNonRecurringEvent(String iEventId) throws IOException {
			Event theTargetEvent = null;
			com.google.api.services.calendar.model.Events allEventsList;

			String aNextPageToken = null;

			while (true) {
				allEventsList = _service.events().list("primary").setPageToken(aNextPageToken)
						.execute();
				java.util.List<Event> allEventItems = allEventsList.getItems();
				for (Event anEvent : allEventItems) {
					String anHtmlLink = anEvent.getHtmlLink();
					if (anHtmlLink != null && anHtmlLink.contains(iEventId)) {
						theTargetEvent = anEvent;
					}
				}
				aNextPageToken = allEventsList.getNextPageToken();
				if (aNextPageToken == null) {
					break;
				}
			}
			return theTargetEvent;
		}

		private static long getNewEndDateTime(int daysToPostpone) {
			long endTimeMillis;
			java.util.Calendar c = java.util.Calendar.getInstance();
			c.add(java.util.Calendar.DATE, daysToPostpone);

			endTimeMillis = c.getTimeInMillis();

			return endTimeMillis;
		}

		private static long getNewStartTime(int daysToPostpone) {
			java.util.Calendar c = java.util.Calendar.getInstance();
			c.add(java.util.Calendar.DATE, daysToPostpone);
			long newStartDateTimeMillis = c.getTimeInMillis();
			System.out.println("getNewStartTime()" + " - New start time:\t" + c.getTime());
			return newStartDateTimeMillis;
		}

		private static Message[] getMessages(Store theImapClient) throws NoSuchProviderException,
				MessagingException {
			Folder folder = openUrgentFolder(theImapClient);
			Message[] msgs = folder.getMessages();
			FetchProfile fp = new FetchProfile();
			fp.add(FetchProfile.Item.ENVELOPE);
			fp.add("X-mailer");
			folder.fetch(msgs, fp);
			return msgs;
		}

		private static Folder openUrgentFolder(Store theImapClient) throws MessagingException {
			Folder folder = theImapClient.getFolder("3 - Urg - time sensitive");
			folder.open(Folder.READ_WRITE);
			return folder;
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

		@SuppressWarnings("unchecked")
		private static String getMessageID(Message aMessage) throws MessagingException {
			Enumeration<Header> allHeaders = (Enumeration<Header>) aMessage.getAllHeaders();
			String messageID = "<not found>";
			while (allHeaders.hasMoreElements()) {
				Header e = (Header) allHeaders.nextElement();
				if (e.getName().equals(MESSAGE_ID)) {
					messageID = e.getValue();
				}
			}
			return messageID;
		}

		private static String getCalendarName(Message aMessage) throws IOException,
				MessagingException {
			String calendarName;
			MimeMultipart s = (MimeMultipart) aMessage.getContent();
			String body1 = (String) s.getBodyPart(0).getContent();
			if (body1.contains("Calendar:")) {
				Pattern pattern = Pattern.compile("Calendar: (.*)");
				Matcher m = pattern.matcher(body1);
				if (!m.find()) {
					throw new RuntimeException("eid not in string 2");
				}
				calendarName = m.group(1);
			} else {
				throw new RuntimeException(aMessage.getSubject());
			}
			return calendarName;
		}

		@SuppressWarnings("serial")
		private static class IsRecurringEventException extends Exception {
			IsRecurringEventException(String message) {
				super(message);
			}
		}
	}

	private static class PostponeToNextFree {

		private static final Calendar _service = HelloWorldResource.getCalendarService();

		private static List<Long> getEventDatesTruncated() {
			// System.out.println("getEventDates() - begin");
			@SuppressWarnings("unchecked")
			List<Long> oEventDates = new TreeList();
			for (Long eventTimeUntruncated : getEventTimes()) {
				long truncateDateTime = truncateDateTime(eventTimeUntruncated);
				oEventDates.add(truncateDateTime);
				System.out.println("NotNow.GetCalendarEvents.getEventDatesTruncated() - "
						+ formatDate(truncateDateTime));
			}
			// System.out.println("getEventDates() - number of events: "
			// + oEventDates.size());
			return oEventDates;
		}

		private static List<Long> getEventTimes() {
			// System.out.println("getEventTimes()  - begin");
			Set<Long> orderedTimes = new TreeSet<Long>();
			Multimap<Long, Event> m = ArrayListMultimap.create();
			try {
				for (Event event : getEventsList()) {
					if (event.getStart() != null) {
						DateTime dateTime = event.getStart().getDateTime();
						long eventTimeMillis;
						if (dateTime == null) {
							eventTimeMillis = event.getStart().getDate().getValue();
						} else {
							eventTimeMillis = dateTime.getValue();
						}
						m.put(eventTimeMillis, event);
						orderedTimes.add(eventTimeMillis);
					} else {
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			return new LinkedList<Long>(orderedTimes);
		}

		private static List<Event> getEventsList() throws IOException {
			int pages = 20;
			List<Event> allItems = new LinkedList<Event>();
			boolean getMoreItems = true;
			String nextPageToken = null;
			int curPage = 0;
			while (getMoreItems) {
				Events eventsMap = _service.events().list(getCalendarId("ss401533@gmail.com"))
						.setTimeMin(new DateTime(System.currentTimeMillis())) // e.g.
																				// 2016-10-02T15:00:00Z
						.setMaxResults(2500).setPageToken(nextPageToken).execute();
				nextPageToken = eventsMap.getNextPageToken();
				List<Event> items = eventsMap.getItems();
				if (items.size() < 1) {
					throw new RuntimeException("No items returned in page " + curPage);
				}
				allItems.addAll(items);
				if (nextPageToken == null) {
					getMoreItems = false;
					break;
				}
				curPage++;
				if (curPage > pages) {
					getMoreItems = false;
					break;
				}
			}
			return allItems;
		}

		private static String getCalendarId(String calendarName) {

			final String string = System.getProperty("user.home") + "/.gcal_task_warrior";
			final File file = new File(string + "/calendars.json");
			String s;
			try {
				s = FileUtils.readFileToString(file, "UTF-8");
				JSONObject j = new JSONObject(s);
				if ("Sridhar Sarnobat".equals(calendarName)) {
					calendarName = "ss401533@gmail.com";
				}
				JSONObject jsonObject = (JSONObject) j.get(calendarName);
				String id = jsonObject.getString("calendar_id");
				return id;
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}

		private static long findNextFreeDate() {
			System.out.println("NotNow.GetCalendarEvents.findNextFreeDate() - begin");
			List<Long> takenDates = PostponeToNextFree.getEventDatesTruncated();
			long currentDate = getCurrentDate();
			long twentyFourHours = 86400000L;
			long nextFreeDate = currentDate + twentyFourHours;
			while (takenDates.contains(nextFreeDate)) {
				System.out.println("findNextFreeDate() - Taken: " + formatDate(nextFreeDate));
				nextFreeDate = truncateDateTime(addOneDay(nextFreeDate));
			}
			if (takenDates.contains(nextFreeDate)) {
				throw new RuntimeException("We should have kept looking for a free date.");
			}
			if (nextFreeDate == currentDate) {
				throw new RuntimeException("nextFreeDate == currentDate");
			}
			return nextFreeDate;
		}

		private static String formatDate(long nextFreeDate) {
			return new Date(nextFreeDate).toString() + " (" + nextFreeDate + ")";
		}

		private static long getCurrentDate() {
			long currentDate = -1;
			java.util.Date now = java.util.Calendar.getInstance().getTime();
			java.util.Date today = DateUtils.truncate(now, java.util.Calendar.DAY_OF_MONTH);
			java.util.Date tomorrow = DateUtils.addDays(today, 1);
			currentDate = tomorrow.getTime();
			return currentDate;
		}

		private static long addOneDay(long inputDate) {
			System.out.println("NotNow.GetCalendarEvents.addOneDay() - in:\t"
					+ formatDate(inputDate));
			Date dayAfter = DateUtils.addDays(new java.util.Date(inputDate), 1);
			long dayAfterTrunc = DateUtils.truncate(dayAfter, java.util.Calendar.DATE).getTime();
			System.out.println("NotNow.GetCalendarEvents.addOneDay() - out:\t"
					+ formatDate(dayAfterTrunc));
			return dayAfterTrunc;
		}

		private static long truncateDateTime(long inputDate) {
			return DateUtils.truncate(new java.util.Date(inputDate), java.util.Calendar.DATE)
					.getTime();
		}

		public static void postponeEventToNextFreeDate(String itemNumber) {
			System.out.println("postponeEventToNextFreeDate() - begin");
			int daysToNextFreeDate = getDaysToNextFreeDate() - 1;
			try {
				Postpone.postpone(itemNumber, Integer.toString(daysToNextFreeDate));
			} catch (IOException e) {
				e.printStackTrace();
			} catch (MessagingException e) {
				e.printStackTrace();
			} catch (GeneralSecurityException e) {
				e.printStackTrace();
			}
		}

		private static int getDaysToNextFreeDate() {
			System.out.println("GetCalendarEvents.getDaysToNextFreeDate() - begin");
			long todayMidnight = DateUtils.truncate(java.util.Calendar.getInstance().getTime(),
					java.util.Calendar.DAY_OF_MONTH).getTime();
			long nextFreeDate = findNextFreeDate();
			java.util.Calendar nextFree = java.util.Calendar.getInstance();
			nextFree.setTimeInMillis(nextFreeDate);
			long daysToNextFreeDate = (nextFreeDate - todayMidnight) / 86400000 + 1;
			return (int) daysToNextFreeDate;
		}

	}
}
