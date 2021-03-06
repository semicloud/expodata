package cn.iscas;

import com.google.common.math.Stats;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.ColumnListHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.json.JSONObject;

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
        resp.setHeader("content-type", "text/json,charset=UTF-8");
        ArrayList<String> parameterNames = Collections.list(req.getParameterNames());
        ServletOutputStream out = resp.getOutputStream();
        if (!parameterNames.contains("date") || !parameterNames.contains("time")) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("nowCount", "NaN");
            jsonObject.put("totalCount", "NaN");
            jsonObject.put("errorMessage", "parameter [date] or [time] not found!");
            out.println(jsonObject.toString());
            out.flush();
            out.close();
            return;
        }
        String date = req.getParameter("date");
        String time = req.getParameter("time");
        log.debug("input parameter: date=" + date + ", time=" + time);
        double estimatedTotalCount = 0;
        double estimatedNowCount = 0;
        ArrayList<String> allDateList = getAllDateList();
        ArrayList<String> workdays = getWorkdays(allDateList);
        ArrayList<String> holidays = getHolidays(allDateList, workdays);
        if (isWorkday(LocalDate.parse(date))) {
            estimatedNowCount = estimate(date, time, workdays, "nowCount");
            estimatedTotalCount = estimate(date, time, workdays, "totalCount");
        } else {
            estimatedNowCount = estimate(date, time, holidays, "nowCount");
            estimatedTotalCount = estimate(date, time, holidays, "totalCount");
        }

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("nowCount", estimatedNowCount);
        jsonObject.put("totalCount", estimatedTotalCount);
        jsonObject.put("errorMessage", "");
        out.println(jsonObject.toString());
        out.flush();
        out.close();
        return;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }

    private ArrayList<String> getExtraDays(String attrName) {
        ServletConfig config = getServletConfig();
        ArrayList<String> names = Collections.list(config.getInitParameterNames());
        ArrayList<String> days = new ArrayList<>();
        if (names.contains(attrName)) {
            String str = getInitParameter(attrName);
            days = new ArrayList<>(Arrays.asList(str.split(",")));
        } else {
            log.error(attrName + " not found in web.xml!");
        }
        log.debug("load " + attrName + ":" + String.join(",", days));
        return days;
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

        List<LocalDate> extraHolidays = getExtraDays(EXTRA_HOLIDAYS).stream()
                .map(s -> LocalDate.parse(s)).collect(Collectors.toList());
        dates.removeAll(extraHolidays);
        log.debug("after remove" + extraHolidays.size() + " extra holidays, " + dates.size() + " dates retained");

        List<LocalDate> extraWorkdays = getExtraDays(EXTRA_WORKDAYS).stream()
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
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            log.error("Database driver not found!");
        }
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

    private String getHour(String time) {
        return time.split(":")[0];
    }

    private String getMinute(String time) {
        return time.split(":")[1];
    }

    boolean isWorkday(LocalDate date) {
        String dateStr = date.toString("yyyy-MM-dd");
        if (getExtraDays(EXTRA_HOLIDAYS).contains(dateStr))
            return false;
        if (getExtraDays(EXTRA_WORKDAYS).contains(dateStr))
            return true;
        if (date.getDayOfWeek() != 6 && date.getDayOfWeek() != 7)
            return true;
        return false;
    }

    ArrayList<Double> getCountByDays(List<String> times, List<String> days, String column) {
        ArrayList<Double> ans = new ArrayList<>();
        StringBuilder sb = new StringBuilder("select id,day,time," + column + " from expo_flow_status where day in ('");
        for (String day : days) {
            if (days.indexOf(day) == days.size() - 1)
                sb.append(day + "')");
            else
                sb.append(day + "','");
        }
        sb.append(" and time in ('");
        for (String time : times) {
            if (times.indexOf(time) == times.size() - 1)
                sb.append(time + "')");
            else
                sb.append(time + "','");
        }
        sb.append("order by day,time");
        log.debug("sql:" + sb.toString());
        ResultSetHandler<List<Double>> h = new ColumnListHandler<>(column);
        try (Connection conn = getJdbcConnection()) {
            QueryRunner runner = new QueryRunner();
            ans = new ArrayList<Double>(runner.query(conn, sb.toString(), h));
        } catch (SQLException e) {
            log.error("Database error! message: " + e.getMessage());
            e.printStackTrace();
        }
        return ans;
    }

    private double estimate(String dateStr, String timeStr, List<String> days, String column) {
        LocalDate date = LocalDate.parse(dateStr);
        log.debug("parsed date:" + date.toString());
        double ans = 0.0;
        int hour = Integer.parseInt(getHour(timeStr));
        log.debug(date + " is a workday..");
        days.remove(dateStr);
        ArrayList<Double> nums = getCountByDays(Arrays.asList(timeStr), days, column);
        nums.removeAll(Collections.singleton(0.0d)); // Remove zeros
        Double timeHistMean = Stats.meanOf(nums);
        log.debug("In work day history, the average of [" + column + "] at [" + timeStr + "] is " + timeHistMean);

        List<String> lastHourTimeStrList = getTimeList(hour - 1, 0, hour - 1, 55, 5);

        ArrayList<Double> numsHistory = getCountByDays(lastHourTimeStrList, days, column);
        ArrayList<Double> numsToday = getCountByDays(lastHourTimeStrList, Arrays.asList(dateStr), column);
        numsHistory.removeAll(Collections.singleton(0.0d));
        numsToday.removeAll(Collections.singleton(0.0d));

        if (numsHistory.isEmpty()) {
            log.error("Data unavailable!");
            return 0.0d;
        }

        if (numsToday.isEmpty()) {
            log.error("Data unavailable!");
            return 0.0d;
        }

        double numsHistoryMean = Math.rint(Stats.meanOf(numsHistory));
        double numsTodayMean = Math.rint(Stats.meanOf(numsToday));

        log.debug("At " + timeStr + ", historical " + column + " average is " +
                numsHistoryMean + ", today average is " + numsTodayMean);

        double ratio = numsTodayMean / numsHistoryMean;
        log.debug("ratio:" + ratio);

        if (ratio >= 1)
            ans = timeHistMean + timeHistMean * Math.abs(1 - ratio);
        else
            ans = timeHistMean - timeHistMean * Math.abs(1 - ratio);
        ans = Math.rint(ans);
        log.debug("Estimate " + column + " at " + timeStr + ": " + ans);

        return ans;
    }
}
