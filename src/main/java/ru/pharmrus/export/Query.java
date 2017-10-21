package ru.pharmrus.export;

public class Query {
    private String  name;
    private String  dependOn;
    private String  query;
    private String[]  parameters = new String[0];
    private Query child;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDependOn() {
        return dependOn;
    }

    public void setDependOn(String dependOn) {
        this.dependOn = dependOn;
    }

    public String[] getParameters() {
        return parameters;
    }

    public void setParameters(String[] parameters) {
        this.parameters = parameters;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Query getChild() {
        return child;
    }

    public void setChild(Query child) {
        this.child = child;
    }
}
