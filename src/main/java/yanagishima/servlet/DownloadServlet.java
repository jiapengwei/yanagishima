package yanagishima.servlet;

import me.geso.tinyorm.TinyORM;
import yanagishima.config.YanagishimaConfig;
import yanagishima.row.Query;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static yanagishima.util.AccessControlUtil.validateDatasource;
import static yanagishima.util.DownloadUtil.tsvDownload;
import static yanagishima.util.HttpRequestUtil.getRequiredParameter;

@Singleton
public class DownloadServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_ENCODE = "UTF-8";

    private final YanagishimaConfig config;
    private final TinyORM db;

    @Inject
    public DownloadServlet(YanagishimaConfig config, TinyORM db) {
        this.config = config;
        this.db = db;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) {
        String queryId = request.getParameter("queryid");
        if (queryId == null) {
            return;
        }

        String datasource = getRequiredParameter(request, "datasource");
        if (config.isCheckDatasource() && !validateDatasource(request, datasource)) {
            try {
                response.sendError(SC_FORBIDDEN);
                return;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        String fileName = queryId + ".tsv";
        String encode = Optional.ofNullable(request.getParameter("encode")).orElse(DEFAULT_ENCODE);
        String header = Optional.ofNullable(request.getParameter("header")).orElse("true");
        boolean showHeader = Boolean.parseBoolean(header);
        if (config.isAllowOtherReadResult(datasource)) {
            tsvDownload(response, fileName, datasource, queryId, encode, showHeader);
            return;
        }
        String userName = request.getHeader(config.getAuditHttpHeaderName());
        requireNonNull(userName, "Username must exist when auditing header name is enabled");
        Optional<Query> query = db.single(Query.class).where("query_id = ? AND datasource = ? AND user = ?", queryId, datasource, userName).execute();
        if (query.isPresent()) {
            tsvDownload(response, fileName, datasource, queryId, encode, showHeader);
        }
    }
}
