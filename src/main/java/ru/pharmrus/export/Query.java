package ru.pharmrus.export;

import java.sql.PreparedStatement;
import java.util.LinkedList;
import java.util.List;

public class Query {
    private String  name;
    private String  dependOn;
    private String  query;
    private String  extWhere;
    private String[]  parameters = new String[0];
    private Query child;
    private WhereParam[] whereParams = new WhereParam[0];

    boolean hasExtWhere() {
        return whereParams.length > 0;
    }

    private class WhereParam {
        private final String  name;
        private final String  type;

        WhereParam(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    public String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getDependOn() {
        return dependOn;
    }

    void setDependOn(String dependOn) {
        this.dependOn = dependOn;
    }

    String[] getParameters() {
        return parameters;
    }

    void setParameters(String[] parameters) {
        this.parameters = parameters;
    }

    String getQuery() {
        return query;
    }

    void setQuery(String query) {
        this.query = query;
    }

    Query getChild() {
        return child;
    }

    void setChild(Query child) {
        this.child = child;
    }

    String getExtWhere() {
        return extWhere;
    }

    void setExtWhere(String extWhere) {
        this.extWhere = extWhere;
        if (extWhere != null && !extWhere.isEmpty()) {
            String[] params = extWhere.split(",");
            List<WhereParam> wps = new LinkedList<>();
            for (String param : params) {
                String[] pparts = param.split(":");
                if (pparts.length != 2)
                    throw (new RuntimeException("Wrong parameter format, should be 'Name:Type'"));

                WhereParam p = new WhereParam(pparts[0].trim(), pparts[1].trim());
                wps.add(p);
            }

            whereParams = wps.toArray(new WhereParam[wps.size()]);
        }
        else {
            whereParams = new WhereParam[0];
        }
    }

    String buildQuery (String ... params) {
        StringBuilder qb = new StringBuilder("");
        for (WhereParam whereParam : whereParams) {
            switch (whereParam.type.toLowerCase()) {
                case "date":
                    qb.append("and ").append(whereParam.name);
                    if (params.length == 2) {
                        qb.append(" between todate('").append(params[0]).append("', 'DD.MM.YYYY') and todate('")
                                .append(params[1]).append("', 'DD.MM.YYYY')");
                    } else if (params.length == 1) {
                        qb.append(" = todate('").append(params[0]).append("', 'DD.MM.YYYY')");
                    } else {
                        return query.replaceAll("<extWhere>", "");
                    }
                    break;

                case "string":
                    break;

                case "number":
                    break;

                default:
            }
        }

        return query.replaceAll("<extWhere>", qb.toString());
    }
}
