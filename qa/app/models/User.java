package models;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

import models.database.Database;

/**
 * A user with a name. Can contain {@link Item}s i.e. {@link Question}s,
 * {@link Answer}s, {@link Comment}s and {@link Vote}s. When deleted, the
 * <code>User</code> requests all his {@link Item}s to delete themselves.
 * 
 * @author Simon Marti
 * @author Mirco Kocher
 * 
 */
public class User implements IObserver {

	private final String name;
	private final String password;
	private String email;
	private final HashSet<Item> items;
	private String fullname;
	protected Date dateOfBirth;
	private String website;
	private String profession;
	private String employer;
	private String biography;

	public static final String DATE_FORMAT_CH = "dd.MM.yyyy";
	public static final String DATE_FORMAT_US = "MM/dd/yyyy";
	public static final String DATE_FORMAT_ISO = "yyyy-MM-dd";

	/**
	 * Creates a <code>User</code> with a given name.
	 * 
	 * @param name
	 *            the name of the <code>User</code>
	 */
	public User(String name, String password) {
		this.name = name;
		this.password = encrypt(password);
		items = new HashSet<Item>();
	}

	/**
	 * Gets the name of the <code>User</code>.
	 * 
	 * @return name of the <code>User</code>
	 */
	public String getName() {
		return name;
	}

	/**
	 * Encrypt the password with SHA-1.
	 * 
	 * @param password
	 * @return the encrypted password
	 */
	public static String encrypt(String password) {
		try {
			MessageDigest m = MessageDigest.getInstance("SHA-1");
			return new BigInteger(1, m.digest(password.getBytes()))
					.toString(16);
		} catch (NoSuchAlgorithmException e) {
			return password;
		}
	}

	/**
	 * Encrypt the password and check if it is the same as the stored one.
	 * 
	 * @param passwort
	 * @return true if the password is right
	 */
	public boolean checkPW(String password) {
		return this.password.equals(encrypt(password));
	}

	/**
	 * Check an email-address to be valid.
	 * 
	 * @param email
	 * @return true if the email is valid.
	 */
	public static boolean checkEmail(String email) {
		return email.matches("\\S+@(?:[A-Za-z0-9-]+\\.)+\\w{2,4}");

	}

	/**
	 * Registers an {@link Item} which should be deleted in case the
	 * <code>User</code> gets deleted.
	 * 
	 * @param item
	 *            the {@link Item} to register
	 */
	public void registerItem(Item item) {
		items.add(item);
	}

	/*
	 * Causes the <code>User</code> to delete all his {@link Item}s.
	 */
	public void delete() {
		// operate on a clone to prevent a ConcurrentModificationException
		HashSet<Item> clone = (HashSet<Item>) items.clone();
		for (Item item : clone) {
			item.unregister();
		}
		items.clear();
		Database.get().users().remove(name);
	}

	/**
	 * Unregisters an {@link Item} which has been deleted.
	 * 
	 * @param item
	 *            the {@link Item} to unregister
	 */
	public void unregister(Item item) {
		items.remove(item);
	}

	/**
	 * Checks if an {@link Item} is registered and therefore owned by a
	 * <code>User</code>.
	 * 
	 * @param item
	 *            the {@link Item}to check
	 * @return true if the {@link Item} is registered
	 */
	public boolean hasItem(Item item) {
		return items.contains(item);
	}

	/**
	 * The amount of Comments, Answers and Questions the <code>User</code> has
	 * posted in the last 60 Minutes.
	 * 
	 * @return The amount of Comments, Answers and Questions for this
	 *         <code>User</code> in this Hour.
	 */
	public int howManyItemsPerHour() {
		Date now = SystemInformation.get().now();
		int i = 0;
		for (Item item : items) {
			if ((now.getTime() - item.timestamp().getTime()) <= 60 * 60 * 1000) {
				i++;
			}
		}
		return i;
	}

	/**
	 * The <code>User</code> is a Cheater if over 50% of his votes is for the
	 * same <code>User</code>.
	 * 
	 * @return True if the <code>User</code> is supporting somebody.
	 */
	public boolean isMaybeCheater() {
		HashMap<User, Integer> votesForUser = new HashMap<User, Integer>();
		for (Item item : items) {
			if (item instanceof Vote && ((Vote) item).up()) {
				Integer count = votesForUser.get(item.owner());
				if (count == null) {
					count = 0;
				}
				votesForUser.put(item.owner(), count + 1);
			}
		}

		if (votesForUser.isEmpty())
			return false;

		Integer maxCount = Collections.max(votesForUser.values());
		return maxCount > 3 && maxCount / votesForUser.size() > 0.5;
	}

	/**
	 * Anonymizes all questions, answers and comments by this user.
	 * 
	 * @param doAnswers
	 *            - whether to anonymize this user's answers as well
	 * @param doComments
	 *            - whether to anonymize this user's comments as well
	 */
	public void anonymize(boolean doAnswers, boolean doComments) {
		// operate on a clone to prevent a ConcurrentModificationException
		HashSet<Item> clone = (HashSet<Item>) items.clone();
		for (Item item : clone) {
			if (item instanceof Question || doAnswers && item instanceof Answer
					|| doComments && item instanceof Comment) {
				((Entry) item).anonymize();
				items.remove(item);
			}
		}
	}

	/**
	 * The <code>User</code> is a Spammer if he posts more than 30 comments,
	 * answers or questions in the last hour.
	 * 
	 * @return True if the <code>User</code> is a Spammer.
	 */
	public boolean isSpammer() {
		int number = howManyItemsPerHour();
		if (number >= 60)
			return true;
		return false;
	}

	/**
	 * Set the <code>User</code> as a Cheater if he spams the Site or supports
	 * somebody.
	 * 
	 */
	public boolean isCheating() {
		return (isSpammer() || isMaybeCheater());
	}

	/**
	 * Calculates the age of the <code>User</code> in years.
	 * 
	 * @return age of the <code>User</code>
	 */
	private int age() {
		Date now = SystemInformation.get().now();
		if (dateOfBirth != null) {
			long age = now.getTime() - dateOfBirth.getTime();
			return (int) (age / ((long) 1000 * 3600 * 24 * 365));
		} else
			return (0);
	}

	/**
	 * Turns the Date object d into a String using the format given in the
	 * constant DATE_FORMAT.
	 */
	private String dateToString(Date d) {
		if (d != null) {
			SimpleDateFormat fmt = new SimpleDateFormat(DATE_FORMAT_CH);
			return fmt.format(d);
		} else
			return null;
	}

	/**
	 * Turns the String object s into a Date assuming the format given in the
	 * constant DATE_FORMAT.
	 * 
	 * @throws ParseException
	 */
	private Date stringToDate(String s) throws ParseException {
		if (Pattern.matches("\\d{1,2}\\.\\d{1,2}\\.\\d{4}", s)) {
			SimpleDateFormat fmt = new SimpleDateFormat(DATE_FORMAT_CH);
			return fmt.parse(s);
		} else if (Pattern.matches("\\d{1,2}/\\d{1,2}/\\d{4}", s)) {
			SimpleDateFormat fmt = new SimpleDateFormat(DATE_FORMAT_US);
			return fmt.parse(s);
		} else if (Pattern.matches("\\d{4}-\\d{1,2}-\\d{1,2}", s)) {
			SimpleDateFormat fmt = new SimpleDateFormat(DATE_FORMAT_ISO);
			return fmt.parse(s);
		} else
			return (null);
	}

	/* Getter and Setter for profile data */

	public void setEmail(String email) {
		this.email = email;
	}

	public String getEmail() {
		return email;
	}

	public void setFullname(String fullname) {
		this.fullname = fullname;
	}

	public String getFullname() {
		return fullname;
	}

	public void setDateOfBirth(String birthday) throws ParseException {
		dateOfBirth = stringToDate(birthday);
	}

	public String getDateOfBirth() {
		return dateToString(dateOfBirth);
	}

	public int getAge() {
		return age();
	}

	public void setWebsite(String website) {
		this.website = website;
	}

	public String getWebsite() {
		return website;
	}

	public void setProfession(String profession) {
		this.profession = profession;
	}

	public String getProfession() {
		return profession;
	}

	public void setEmployer(String employer) {
		this.employer = employer;
	}

	public String getEmployer() {
		return employer;
	}

	public void setBiography(String biography) {
		this.biography = biography;
	}

	public String getBiography() {
		return biography;
	}

	public String getSHA1Password() {
		return password;
	}

	/**
	 * Start observing changes for an entry (e.g. new answers to a question).
	 * 
	 * @param what
	 *            the entry to watch
	 */
	public void startObserving(IObservable what) {
		what.addObserver(this);
	}

	/**
	 * Checks if a specific entry is being observed for changes.
	 * 
	 * @param what
	 *            the entry to check
	 */
	public boolean isObserving(IObservable what) {
		return what.hasObserver(this);
	}

	/**
	 * Stop observing changes for an entry (e.g. new answers to a question).
	 * 
	 * @param what
	 *            the entry to unwatch
	 */
	public void stopObserving(IObservable what) {
		what.removeObserver(this);
	}

	/**
	 * @see models.IObserver#observe(models.IObservable, java.lang.Object)
	 */
	public void observe(IObservable o, Object arg) {
		if (o instanceof Question && arg instanceof Answer
				&& ((Answer) arg).owner() != this) {
			new Notification(this, (Answer) arg);
		}
	}

	/**
	 * Registers a new <code>User</code> to the database.
	 * 
	 * @param username
	 * @param password
	 *            of the <code>User</code>
	 * @return user
	 */

	/**
	 * Get a List of the last three <code>Question</code>s of this
	 * <code>User</code>.
	 * 
	 * @return List<Question> The last three <code>Question</code>s of this
	 *         <code>User</code>
	 */
	public List<Question> getRecentQuestions() {
		List<Question> recentQuestions = getQuestions();
		Collections.sort(recentQuestions, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((Item) o2).timestamp().compareTo(
						((Item) o1).timestamp());
			}
		});
		if (recentQuestions.size() > 3)
			return recentQuestions.subList(0, 3);
		return recentQuestions;
	}

	/**
	 * Get a List of the last three <code>Answer</code>s of this
	 * <code>User</code>.
	 * 
	 * @return List<Answer> The last three <code>Answer</code>s of this
	 *         <code>User</code>
	 */
	public List<Answer> getRecentAnswers() {
		List<Answer> recentAnswers = getAnswers();
		Collections.sort(recentAnswers, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((Item) o2).timestamp().compareTo(
						((Item) o1).timestamp());
			}
		});
		if (recentAnswers.size() > 3)
			return recentAnswers.subList(0, 3);
		return recentAnswers;
	}

	/**
	 * Get a List of the last three <code>Comment</code>s of this
	 * <code>User</code>.
	 * 
	 * @return List<Comment> The last three <code>Comment</code>s of this
	 *         <code>User</code>
	 */
	public List<Comment> getRecentComments() {
		List<Comment> recentComments = getComments();
		Collections.sort(recentComments, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((Item) o2).timestamp().compareTo(
						((Item) o1).timestamp());
			}
		});
		if (recentComments.size() > 3)
			return recentComments.subList(0, 3);
		return recentComments;
	}

	/*
	 * Interface to gather statistical data
	 */

	public static int getUserCount() {
		return Database.get().users().count();
	}

	/**
	 * Get a sorted ArrayList of all <code>Questions</code>s of this
	 * <code>User</code>.
	 * 
	 * @return ArrayList<Question> All questions of this <code>User</code>
	 */
	public ArrayList<Question> getQuestions() {
		return getItemsByType(Question.class, null);
	}

	/**
	 * Get a sorted ArrayList of all <code>Answer</code>s of this
	 * <code>User</code>.
	 * 
	 * @return ArrayList<Answer> All <code>Answer</code>s of this
	 *         <code>User</code>
	 */
	public ArrayList<Answer> getAnswers() {
		return getItemsByType(Answer.class, null);
	}

	/**
	 * Get a sorted ArrayList of all <code>Comment</code>s of this
	 * <code>User</code>
	 * 
	 * @return ArrayList<Comment> All <code>Comments</code>s of this
	 *         <code>User</code>
	 */
	public ArrayList<Comment> getComments() {
		return getItemsByType(Comment.class, null);
	}

	/**
	 * Get an ArrayList of all best rated answers
	 * 
	 * @return ArrayList<Answer> All best rated answers
	 */
	public ArrayList<Answer> bestAnswers() {
		return getItemsByType(Answer.class, "isBestAnswer");
	}

	/**
	 * Get an ArrayList of all highRated answers
	 * 
	 * @return ArrayList<Answer> All high rated answers
	 */
	public ArrayList<Answer> highRatedAnswers() {
		return getItemsByType(Answer.class, "isHighRated");
	}

	/**
	 * Get an ArrayList of all notifications of this user, sorted most-recent
	 * one first and optionally fulfilling one filter criterion.
	 * 
	 * @param filter
	 *            an optional name of a filter method (e.g. "isNew")
	 * @return ArrayList<Notification> All notifications of this user
	 */
	protected ArrayList<Notification> getAllNotifications(String filter) {
		ArrayList<Notification> result = new ArrayList<Notification>();
		/*
		 * Hack: remove all notifications to deleted answers
		 * 
		 * unfortunately, there's currently no other way to achieve this, as
		 * there is no global list of all existing notifications nor an easy way
		 * to register all users for observing the deletion of answers (because
		 * there's no global list of all existing users, either)
		 */
		ArrayList<Notification> notifications = getItemsByType(
				Notification.class, filter);
		for (Notification n : notifications) {
			if (n.getAbout() instanceof Answer) {
				Answer answer = (Answer) n.getAbout();
				if (answer.getQuestion() != null) {
					result.add(n);
				} else {
					n.unregister();
				}
			}
		}
		return result;
	}

	/**
	 * Get an ArrayList of all notifications of this user, sorted most-recent
	 * one first.
	 * 
	 * @return ArrayList<Notification> All notifications of this user
	 */
	public ArrayList<Notification> getNotifications() {
		return getAllNotifications(null);
	}

	/**
	 * Get an ArrayList of all unread notifications of this user
	 * 
	 * @return the unread notifications
	 */
	public ArrayList<Notification> getNewNotifications() {
		return getAllNotifications("isNew");
	}

	/**
	 * Gets the most recent unread notification, if there is any very recent one
	 * 
	 * @return a very recent notification (or null, if there isn't any)
	 */
	public Notification getVeryRecentNewNotification() {
		for (Notification n : getNewNotifications())
			if (n.isVeryRecent())
				return n;
		return null;
	}

	/**
	 * Gets a notification by its id value.
	 * 
	 * NOTE: slightly hacky since we don't track notifications in a separate
	 * IDTable but in this.items like everything else - this should get fixed
	 * once we migrate to using a real DB.
	 * 
	 * @param id
	 *            the notification's id
	 * @return a notification with the given id
	 */
	public Notification getNotification(int id) {
		for (Notification n : getNotifications())
			if (n.getID() == id)
				return n;
		return null;
	}

	/**
	 * Get an ArrayList of all items of this user being an instance of a
	 * specific type and optionally fulfilling an additional filter criterion.
	 * 
	 * @param type
	 *            the type
	 * @param filter
	 *            an optional name of a filter method which has to be available
	 *            on all objects, must not need any arguments and must return a
	 *            boolean value
	 * @return ArrayList All type-items of this user
	 */
	protected ArrayList getItemsByType(Class type, String filter) {
		ArrayList items = new ArrayList();
		for (Item item : this.items) {
			if (type.isInstance(item)) {
				if (filter != null) {
					try {
						if (!(Boolean) type.getMethod(filter).invoke(item)) {
							continue;
						}
					} catch (Exception ex) {
						// reflection APIs throw half a dozen different
						// exceptions, let's just abort if we hit any of them
						// for now
						return null;
					}
				}
				items.add(item);
			}
		}
		Collections.sort(items);
		return items;
	}
}
