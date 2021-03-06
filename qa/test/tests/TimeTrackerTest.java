package tests;

import java.util.Calendar;
import java.util.GregorianCalendar;

import models.TimeTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import play.test.UnitTest;

public class TimeTrackerTest extends UnitTest {

	private GregorianCalendar g;
	private GregorianCalendar now;
	private TimeTracker t;

	@Before
	public void setUp() throws Exception {
		g = new GregorianCalendar(2010, Calendar.SEPTEMBER, 1);
		now = new GregorianCalendar(2010, Calendar.DECEMBER, 1);
		t = new TimeTracker(g);
	}

	@Test
	public void shouldCalculateNinetyOneDays() {
		int days = t.getDays(now);
		assertEquals(91, days);
	}

	@Test
	public void shouldCalculateOneDay() {
		GregorianCalendar l = new GregorianCalendar(2010, Calendar.SEPTEMBER, 2);
		int days = t.getDays(l);
		assertEquals(1, days);
	}

	@Test
	public void shouldCalculateZeroDays() {
		GregorianCalendar l = new GregorianCalendar(2010, Calendar.SEPTEMBER, 1);
		int days = t.getDays(l);
		assertEquals(0, days);
	}

	@Test
	public void shouldCalculateOneWeek() {
		GregorianCalendar l = new GregorianCalendar(2010, Calendar.SEPTEMBER, 2);
		int weeks = t.getWeeks(l);
		assertEquals(1, weeks);
	}

	@Test
	public void shouldCalculateZeroWeeks() {
		GregorianCalendar f = new GregorianCalendar(2010, Calendar.SEPTEMBER, 1);
		int weeks = t.getWeeks(f);
		assertEquals(0, weeks);
	}

	@Test
	public void shouldCalculateThirteenWeeks() {
		int weeks = t.getWeeks(now);
		assertEquals(13, weeks);
	}

	@Test
	public void shouldCalculateThreeMonths() {
		int months = t.getMonths(now);
		assertEquals(3, months);
	}

	@Test
	public void shouldCalculateOneMonth() {
		GregorianCalendar l = new GregorianCalendar(2010, Calendar.SEPTEMBER, 3);
		int months = t.getMonths(l);
		assertEquals(1, months);
	}

	@Test
	public void shouldCalculateZeroMonths() {
		GregorianCalendar l = new GregorianCalendar(2010, Calendar.SEPTEMBER, 1);
		int months = t.getMonths(l);
		assertEquals(0, months);
	}

	@After
	public void tearDown() {

	}
}
