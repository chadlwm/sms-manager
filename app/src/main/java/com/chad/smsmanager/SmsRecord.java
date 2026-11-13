package com.chad.smsmanager;

/** A single system SMS row. Text is held only in memory, never logged by this model. */
public final class SmsRecord {
    public final long id, date;
    public final String address, body;
    public final int type;
    public SmsRecord(long id, String address, String body, long date, int type) {
        this.id=id; this.address=address; this.body=body; this.date=date; this.type=type;
    }
}
