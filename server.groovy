import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONObject;

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.sun.net.httpserver.HttpServer;

public class NotNow {
	@Path("not_now")
	public static class HelloWorldResource { // Must be public
		private static final String string = "/home/sarnobat/.gcal_task_warrior";
		private static final File file = new File(string + "/tasks.json");
		
		@GET
		@Path("items")
		@Produces("application/json")
		public Response listItems(@QueryParam("rootId") Integer iRootId) throws IOException {
			System.out.println("1");
			JsonObjectBuilder json = Json.createObjectBuilder();
			System.out.println("2");
			String s = FileUtils.readFileToString(file);
			System.out.println(s);
			json.add("tasks", s);
			System.out.println("3");
			return Response.ok().header("Access-Control-Allow-Origin", "*")
					.entity(json.build().toString()).type("application/json")
					.build();
		}
	}

	private static class ListDisplaySynchronous {
		static final String string = "/home/sarnobat/.gcal_task_warrior";
		static final File file = new File(string + "/tasks.json");

//		public static void main(String[] args) throws NoSuchProviderException,
//				MessagingException, IOException {
//
//			writeCalendarsToFileInSeparateThread();
//			getErrands();
//
//			System.out.println("List updated");
//		}

		static void writeCalendarsToFileInSeparateThread() {
			new Thread() {
				public void run() {
					writeCalendars();
				}
			}.start();
		}

		private static void writeCalendars() {

			JSONObject json;
			try {
				json = getCalendars();

				final String string = "/home/sarnobat/.gcal_task_warrior";
				final File file = new File(string + "/calendars.json");
				FileUtils.writeStringToFile(file, json.toString(2), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			} catch (GeneralSecurityException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private static JSONObject getCalendars() throws GeneralSecurityException,
				IOException, UnsupportedEncodingException {
			Calendar client = getCalendarService();

			System.out.println("Getting calendars...");
			@SuppressWarnings("unchecked")
			List<CalendarListEntry> allCalendars = (List<CalendarListEntry>) client
					.calendarList().list().execute().get("items");
			JSONObject json = new JSONObject();

			for (CalendarListEntry aCalendar : allCalendars) {
				// System.out.println(aCalendar.getSummary() + "::"
				// + URLDecoder.decode(aCalendar.getId(), "UTF-8"));
				json.put(aCalendar.getSummary(),
						new JSONObject().put("calendar_id", aCalendar.getId()));
			}
			return json;
		}

		/************************************************************************
		 * Boilerplate
		 ************************************************************************/

		private static Calendar getCalendarService()
				throws GeneralSecurityException, IOException {
			System.out.println("Authenticating...");

			HttpTransport httpTransport = GoogleNetHttpTransport
					.newTrustedTransport();
			Calendar client = new Calendar.Builder(
					httpTransport,
					JacksonFactory.getDefaultInstance(),
					new AuthorizationCodeInstalledApp(
							new GoogleAuthorizationCodeFlow.Builder(
									httpTransport,
									JacksonFactory.getDefaultInstance(),
									GoogleClientSecrets.load(
											JacksonFactory.getDefaultInstance(),
											new InputStreamReader(
													Preconditions.checkNotNull(ListDisplaySynchronous.class
															.getResourceAsStream("/client_secrets.json")))),
									ImmutableSet.of(CalendarScopes.CALENDAR,
											CalendarScopes.CALENDAR_READONLY))
									.setDataStoreFactory(
											new FileDataStoreFactory(
													new java.io.File(
															System.getProperty("user.home"),
															".store/calendar_sample")))
									.build(), new LocalServerReceiver())
							.authorize("user")).setApplicationName(
					"gcal-task-warrior").build();
			return client;

		}

		static void getErrands() throws NoSuchProviderException,
				MessagingException, IOException {
			Message[] msgs = getMessages();
			System.out.println("Messages obtained");

			int postponeCount = getPostponeCount();
			JSONObject json = createJsonListOfEvents(msgs);
			json.put("daysToPostpone", postponeCount);
			
			FileUtils.writeStringToFile(file, json.toString(2));
		}

		private static int getPostponeCount() throws IOException {
			int DAYS_TO_POSTPONE = 30;
			int daysToPostponeSaved = DAYS_TO_POSTPONE;
			if (!file.exists()) {
				daysToPostponeSaved = DAYS_TO_POSTPONE;
			} else {
				String errands = FileUtils.readFileToString(file);

				JSONObject allErrandsJsonOriginal = new JSONObject(errands);

				if (allErrandsJsonOriginal.has("daysToPostpone")) {
					daysToPostponeSaved = allErrandsJsonOriginal
							.getInt("daysToPostpone");
				} else {
					daysToPostponeSaved = DAYS_TO_POSTPONE;
				}
			}
			return daysToPostponeSaved;
		}

		private static JSONObject createJsonListOfEvents(Message[] msgs)
				throws MessagingException {

			// JSONObject jsonToBeSaved = new JSONObject();
			Map<String, JSONObject> messages = new TreeMap<String, JSONObject>();
			// int i = 0;
			for (Message aMessage : msgs) {
				// i++;
				JSONObject messageMetadata = Preconditions
						.checkNotNull(getMessageMetadata(aMessage));
				String[] aTitle = messageMetadata.getString("title").split("@");
				String repeating = "";
				if (aTitle.length > 1 && aTitle[1].contains("Repeating")) {
					repeating = "[Repeating] ";
				}
				String aTitleMain = aTitle[0].replace("Reminder: ", "");
				String printedTitle = repeating + aTitleMain;

				messages.put(StringUtils.capitalize(printedTitle),
						messageMetadata);
				// jsonToBeSaved.put(Integer.toString(i), messageMetadata);
			}
			int i = 0;
			JSONObject jsonToBeSaved = new JSONObject();
			for (String aTitle : new TreeSet<String>(messages.keySet())) {
				++i;
				JSONObject messageMetadata = messages.get(aTitle);
				System.out.println(i + "\t" + aTitle);
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
				// getBodyMetadataSlow(aMessage);
				return errandJsonObject;
			} catch (MessagingException e) {
				e.printStackTrace();
			}
			return null;
		}

		private static Message[] getMessages() throws NoSuchProviderException,
				MessagingException {
			System.out.println("Connecting");
			Store theImapClient = connect();
			Folder folder = theImapClient
					.getFolder("3 - Urg - time sensitive - this week");
			System.out.println("Opening");
			folder.open(Folder.READ_ONLY);

			System.out.println("Getting Message list");
			Message[] msgs = folder.getMessages();

			FetchProfile fp = new FetchProfile();
			fp.add(FetchProfile.Item.ENVELOPE);
			System.out.print("Fetching attributes...");
			folder.fetch(msgs, fp);
			System.out.println("done");
			return msgs;
		}

		private static Store connect() throws NoSuchProviderException,
				MessagingException {
			Properties props = System.getProperties();
			String password = System.getenv("GMAIL_PASSWORD");
			if (password == null) {
				throw new RuntimeException(
						"Please specify your password by running export GMAIL_PASSWORD=mypassword groovy mail.groovy");
			}
			props.setProperty("mail.store.protocol", "imap");
			Store theImapClient = Session.getInstance(props).getStore("imaps");
			theImapClient.connect("imap.gmail.com", "sarnobat.hotmail@gmail.com",
					password);
			return theImapClient;
		}
	}

	public static void main(String[] args) throws URISyntaxException, NoSuchProviderException, MessagingException, IOException {
		
		new Thread() {
			public void run() {
				try {
					ListDisplaySynchronous.getErrands();
				} catch (NoSuchProviderException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (MessagingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}.start();
		ListDisplaySynchronous.writeCalendarsToFileInSeparateThread();
		HttpServer server = JdkHttpServerFactory.createHttpServer(new URI(
				"http://localhost:4456/"), new ResourceConfig(
				HelloWorldResource.class));
	}
}