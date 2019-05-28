package cn.iscas;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
import java.util.*;

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
}
