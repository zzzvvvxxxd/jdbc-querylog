package me.geso.jdbcquerylog;

import de.vandermeer.asciitable.v2.RenderedTable;
import de.vandermeer.asciitable.v2.V2_AsciiTable;
import de.vandermeer.asciitable.v2.render.V2_AsciiTableRenderer;
import de.vandermeer.asciitable.v2.render.WidthLongestLine;
import de.vandermeer.asciitable.v2.themes.V2_E_TableThemes;
import me.geso.jdbctracer.PreparedStatementListener;

import java.sql.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class PreparedStatementLogger implements PreparedStatementListener {
    private static final Pattern RE = Pattern.compile("(\\?)");
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QueryLogDriver.class);

    @Override
    public void trace(Connection connection, long elapsed, String query, List<Object> args) throws SQLException {
        if (!QueryLogDriver.isEnabled()) {
            return;
        }

        query = bind(query, args);
        if (QueryLogDriver.isCompact()) {
            query = compact(query);
        }
        System.err.println(query);
        if (QueryLogDriver.isExplain()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement("EXPLAIN " + query)) {
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    V2_AsciiTable at = new V2_AsciiTable();
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    while (resultSet.next()) {
                        at.addRule();
                        at.addRow(IntStream.rangeClosed(1, columnCount)
                                .mapToObj(i -> {
                                    try {
                                        return metaData.getColumnName(i);
                                    } catch (SQLException e) {
                                        throw new RuntimeException(e);
                                    }
                                }).toArray(String[]::new));
                        at.addRule();
                        at.addRow(IntStream.rangeClosed(1, columnCount)
                                .mapToObj(i -> {
                                    try {
                                        return resultSet.getString(i);
                                    } catch (SQLException e) {
                                        throw new RuntimeException(e);
                                    }
                                }).toArray(String[]::new));
                        at.addRule();
                    }

                    V2_AsciiTableRenderer rend = new V2_AsciiTableRenderer();
                    rend.setTheme(V2_E_TableThemes.UTF_LIGHT.get());
                    rend.setWidth(new WidthLongestLine());
                    RenderedTable render = rend.render(at);
                    for (String line : render.toString().split("\n")) {
                        System.err.println(line);
                    }
                }
            }
        }
    }

    public String compact(String query) {
        return query.replaceAll("\\n", " ");
    }

    private String bind(String query, List<Object> binds) {
        int idx = 0;
        Matcher matcher = RE.matcher(query);
        boolean result = matcher.find();
        if (result) {
            StringBuffer sb = new StringBuffer();
            do {
                Object value = binds.get(idx++);
                if (value instanceof Integer || value instanceof Long) {
                    matcher.appendReplacement(sb, String.valueOf(value));
                } else {
                    matcher.appendReplacement(sb, '"' + String.valueOf(value) + '"');
                }
                result = matcher.find();
            } while (result);
            matcher.appendTail(sb);
            return sb.toString();
        }
        return query.toString();
    }
}