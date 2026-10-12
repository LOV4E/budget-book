package com.dsh.budgetbook;

import org.json.JSONObject;

/** One record: either an income or an expense. */
public class Txn {

    public static final int EXPENSE = 0;
    public static final int INCOME = 1;

    public long id;
    public int type;
    public long cents;
    public String category = "";
    public String note = "";
    /** yyyy-MM-dd */
    public String date = "";
    /** yyyy-MM */
    public String month = "";
    public long created;

    public boolean isIncome() {
        return type == INCOME;
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("type", type);
        o.put("cents", cents);
        o.put("category", category == null ? "" : category);
        o.put("note", note == null ? "" : note);
        o.put("date", date == null ? "" : date);
        o.put("month", month == null ? "" : month);
        o.put("created", created);
        return o;
    }

    public static Txn fromJson(JSONObject o) {
        Txn t = new Txn();
        t.id = o.optLong("id", 0L);
        t.type = o.optInt("type", EXPENSE);
        t.cents = o.optLong("cents", 0L);
        t.category = o.optString("category", "");
        t.note = o.optString("note", "");
        t.date = o.optString("date", "");
        t.month = o.optString("month", "");
        t.created = o.optLong("created", 0L);
        return t;
    }
}
