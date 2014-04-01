import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;

import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Header;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

public class Delete {

	private static final String DIR_PATH = "/Users/sarnobat/.gcal_task_warrior";

	private static final File mTasksFileLatest = new File(DIR_PATH
			+ "/tasks.json");

	public static void main(String[] args) throws IOException,
			NoSuchProviderException, MessagingException {
		String itemToDelete = args[0];

		JSONObject eventJson = getEventJson(itemToDelete, mTasksFileLatest);
		String title = eventJson.getString("title");
		System.out.println("Title:\t" + title);
		Message msg = getMessage(title);
		String messageIdToDelete = getMessageID(msg);
		commit(itemToDelete, messageIdToDelete);

	}

	private static JSONObject getEventJson(String itemToDelete, String errands) {
		JSONObject allErrandsJson = new JSONObject(errands);
		JSONObject eventJson = (JSONObject) allErrandsJson.get(itemToDelete);
		return eventJson;
	}

	// Still useful
	private static JSONObject getEventJson(String itemToDelete,
			File tasksFileLastDisplayed) throws IOException {
		String errands = FileUtils.readFileToString(tasksFileLastDisplayed);
		JSONObject eventJson = getEventJson(itemToDelete, errands);
		return eventJson;
	}

	private static Message getMessage(String title)
			throws NoSuchProviderException, MessagingException {
		Message[] msgs = getMessages();
		Message msg = null;
		for (Message aMsg : msgs) {
			if (aMsg.getSubject().equals(title)) {
				msg = aMsg;
				break;
			}
		}
		if (msg == null) {
			throw new RuntimeException();
		}
		return msg;
	}

	private static Message[] getMessages() throws NoSuchProviderException,
			MessagingException {
		Store theImapClient = connect();
		Folder folder = theImapClient
				.getFolder("3 - Urg - time sensitive - this week");
		folder.open(Folder.READ_WRITE);

		Message[] msgs = folder.getMessages();

		FetchProfile fp = new FetchProfile();
		fp.add(FetchProfile.Item.ENVELOPE);
		fp.add("X-mailer");
		folder.fetch(msgs, fp);
		return msgs;
	}

	private static String getMessageID(Message aMessage)
			throws MessagingException {
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

	private static void commit(String itemToDelete,
			final String messageIdToDelete) throws NoSuchProviderException,
			MessagingException, IOException {

		// All persistent changes are done right at the end, so that any
		// exceptions can get thrown first.
		new Thread() {
			@Override
			public void run() {
				try {
					deleteEmail(messageIdToDelete);
				} catch (NoSuchProviderException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (MessagingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}.start();

	}

	private static void deleteEmail(String messageIdToDelete)
			throws NoSuchProviderException, MessagingException {
		Message[] messages;
		messages = getMessages();
		for (Message aMessage : messages) {
			String aMessageID = getMessageID(aMessage);
			if (aMessageID.equals(messageIdToDelete)) {
				aMessage.setFlag(Flags.Flag.DELETED, true);
				System.out.println("Deleted email:\t" + aMessage.getSubject());
				break;
			}
		}

	}
}
