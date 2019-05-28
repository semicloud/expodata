package cn.iscas;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class PredictNowCountTotalCount extends HttpServlet {
    private static Log log = LogFactory.getLog(PredictNowCountTotalCount.class);
    private static final String EXTRA_HOLIDAYS = "date.extra_holidays";
    private static final String EXTRA_WORKDAYS = "date.extra_workdays";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        ServletOutputStream out = resp.getOutputStream();
        ServletConfig config = getServletConfig();
        String url = config.getInitParameter("db.url");
        System.out.println(url);
        String username = config.getInitParameter("db.username");
        System.out.println(username);
        String password = config.getInitParameter("db.password");
        System.out.println(password);
        System.out.println("Connecting database...");

        ArrayList<String> extraHolidays = getExtraHolidays();
        ArrayList<String> extraWorkdays = getExtraWorkDays();

        out.println("Extra Workdays:");
        for (String s : extraWorkdays) {
            out.println(s);
        }

        out.println("Extra Holidays:");
        for (String s : extraHolidays) {
            out.println(s);
        }

        LocalTime time = new LocalTime(9, 0, 0);
        log.debug("start time: " + time.toString());

        time = time.plusMinutes(5);
        log.debug("plus 5 minutes: " + time.toString());

        ArrayList<String> timeStrs = getTimeList(9, 0, 20, 0, 5);

        ArrayList<String> allDates = getAllDateList();

        ArrayList<String> workdays = getWorkdays(allDates);
        ArrayList<String> holidays = getHolidays(allDates, workdays);

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            out.println("Database connected!");
            out.flush();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot connect the database!", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }

    private ArrayList<String> getExtraHolidays() {
        ServletConfig config = getServletConfig();
        ArrayList<String> names = Collections.list(config.getInitParameterNames());
        ArrayList<String> extraHolidays = new ArrayList<>();
        if (names.contains(EXTRA_HOLIDAYS)) {
            String str = getInitParameter(EXTRA_HOLIDAYS);
            extraHolidays = new ArrayList<>(Arrays.asList(str.split(",")));
        } else {
            log.error(EXTRA_HOLIDAYS + " not found in web.xml!");
        }
        log.debug("load " + EXTRA_HOLIDAYS + ":" + String.join(",", extraHolidays));
        return extraHolidays;
    }

    private ArrayList<String> getExtraWorkDays() {
        ServletConfig config = getServletConfig();
        ArrayList<String> names = Collections.list(config.getInitParameterNames());
        ArrayList<String> extraWorkdays = new ArrayList<>();
        if (names.contains(EXTRA_WORKDAYS)) {
            String str = getInitParameter(EXTRA_WORKDAYS);
            extraWorkdays = new ArrayList<>(Arrays.asList(str.split(",")));
        } else {
            log.error(EXTRA_WORKDAYS + " not found in web.xml!");
        }
        log.debug("load " + EXTRA_WORKDAYS + ":" + String.join(",", extraWorkdays));
        return extraWorkdays;
    }

    private ArrayList<String> getTimeList(int startHour, int startMinute, int endHour, int endMinute, int minuteSpan) {
        LocalTime startTime = new LocalTime(startHour, startMinute, 0);
        LocalTime endTime = new LocalTime(endHour, endMinute, 0);
        ArrayList<String> timeStrs = new ArrayList<>();
        while (startTime.getMillisOfDay() <= endTime.getMillisOfDay()) {
            timeStrs.add(startTime.toString("HH:mm"));
            startTime = startTime.plusMinutes(minuteSpan);
        }
        log.debug("time strs:" + String.join(",", timeStrs));
        return timeStrs;
    }

    private ArrayList<String> getAllDateList() {
        ArrayList<String> ans = new ArrayList<>();
        final String sql = "select distinct day from expo_flow_status order by day;";
        QueryRunner run = new QueryRunner();
        ResultSetHandler<List<String>> h = new ColumnListHandler<String>("day");
        try (Connection conn = getJdbcConnection()) {
            ans = new ArrayList<>(run.query(conn, sql, h));
        } catch (SQLException e) {
            log.error("Database error! message: " + e.getMessage());
            e.printStackTrace();
        }
        log.debug("All dates from database: " + String.join(",", ans));
        return ans;
    }

    private ArrayList<String> getWorkdays(ArrayList<String> allDateList) {
        List<LocalDate> dates = allDateList.stream().map(s -> LocalDate.parse(s)).collect(Collectors.toList());
        log.debug(dates.size() + " dates found and parsed");

        // In joda time, week day is similar to our custom 1 represents monday, 2 for tuesday and so on..
        dates.removeIf(d -> d.getDayOfWeek() == 6 || d.getDayOfWeek() == 7);
        log.debug("after remove saturday and sunday," + dates.size() + " dates retained");

        List<LocalDate> extraHolidays = getExtraHolidays().stream()
                .map(s -> LocalDate.parse(s)).collect(Collectors.toList());
        dates.removeAll(extraHolidays);
        log.debug("after remove" + extraHolidays.size() + " extra holidays, " + dates.size() + " dates retained");

        List<LocalDate> extraWorkdays = getExtraWorkDays().stream()
                .map((s -> LocalDate.parse(s))).collect(Collectors.toList());
        dates.addAll(extraWorkdays);
        log.debug("after add " + extraWorkdays.size() + " extra workdays, " + dates.size() + " dates retained");

        dates.sort(LocalDate::compareTo);

        ArrayList<String> ans = new ArrayList<>(dates.stream().map(d -> d.toString("yyyy-MM-dd")).collect(Collectors.toList()));
        log.debug(dates.size() + " Workdays found:" + String.join(",", ans));
        return ans;
    }

    private ArrayList<String> getHolidays(ArrayList<String> allDateList, ArrayList<String> workdays) {
        allDateList.removeAll(workdays);
        log.debug(allDateList.size() + " Holidays found: " + String.join(",", allDateList));
        return allDateList;
    }

    private Connection getJdbcConnection() throws SQLException {
        return DriverManager.getConnection(getJdbcUrl(), getUserName(), getPassword());
    }

    private String getJdbcUrl() {
        return getServletParam("db.url");
    }

    private String getUserName() {
        return getServletParam("db.username");
    }

    private String getPassword() {
        return getServletParam("db.username");
    }

    private String getServletParam(String paramName) {
        ServletConfig config = getServletConfig();
        ArrayList<String> paramNames = Collections.list(config.getInitParameterNames());
        if (!paramNames.contains(paramName)) {
            log.error("configuration " + paramName + " not found in web.xml!");
        }
        String paramValue = getInitParameter(paramName);
        log.debug("load configuration, name=" + paramName + ", value=" + paramValue);
        return paramValue;
    }
}
