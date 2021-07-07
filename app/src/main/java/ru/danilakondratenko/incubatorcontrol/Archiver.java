package ru.danilakondratenko.incubatorcontrol;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;

public class Archiver {
    /* Archive constants */
    public static final String ARCHIVE_FILE_NAME = "archive.dat";
    public static final int RECORD_SIZE = 16;

    /* Archive state masks */
    public static final byte ST_ZERO    = 0b00000000;

    public static final byte ST_HEATER  = 0b00000001;
    public static final byte ST_WETTER  = 0b00000010;
    public static final byte ST_COOLER  = 0b00000100;
    public static final byte ST_CHAMBER = 0b00111000;
    public static final byte ST_POWER   = 0b01000000;

    public static final byte ST_CHAMBER_LEFT    = 0b00111000;
    public static final byte ST_CHAMBER_NEUTRAL = 0b00000000;
    public static final byte ST_CHAMBER_RIGHT   = 0b00001000;
    public static final byte ST_CHAMBER_ERROR   = 0b00010000;
    public static final byte ST_CHAMBER_UNDEF   = 0b00011000;

    public static final int ST_CHAMBER_SHIFT = 3;

    /* Archive error masks */

    public static final byte ER_ZERO          = 0b00000000;

    public static final byte ER_OVERHEAT      = 0b00000001;
    public static final byte ER_CHAMBER_ERROR = 0b00000100;
    public static final byte ER_NO_INTERNET   = (byte) 0b10000000;

    /* Archive record indexes */

    public static final int TIMESTAMP          =  0;
    public static final int CUR_TEMP           =  8;
    public static final int CUR_HUMID          = 10;
    public static final int ST                 = 12;
    public static final int NEEDED_TEMP        = 13;
    public static final int NEEDED_HUMID       = 14;
    public static final int ER                 = 15;

    /* Archive record data length */

    public static final int TIMESTAMP_LEN      = 8;
    public static final int CUR_TEMP_LEN       = 2;
    public static final int CUR_HUMID_LEN      = 2;

    Context context;

    Archiver(Context context) {
        this.context = context;
    }

    public File getLocalArchiveFile() {
        File result = null;
        boolean resultState = true;
        try {
            result = new File(context.getFilesDir(), ARCHIVE_FILE_NAME);
            if (!result.exists())
                resultState = resultState && result.createNewFile();
            resultState = resultState && result.setWritable(true);
            resultState = resultState && result.setReadable(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (resultState)
            return result;
        else
            return null;
    }

    public byte[] getLocalArchiveRecord(IncubatorState state, IncubatorConfig cfg) {
        short curTemp, curHumid;
        curTemp = (short)(state.currentTemperature * 256);
        curHumid = (short)(state.currentHumidity * 256);

        byte st = ST_ZERO;
        st  = state.heater ? ST_HEATER : ST_ZERO;
        st |= state.wetter ? ST_WETTER : ST_ZERO;
        st |= state.cooler ? ST_COOLER : ST_ZERO;

        st |= ((byte)state.chamber << ST_CHAMBER_SHIFT) & ST_CHAMBER;

        st |= (state.power ? ST_POWER : ST_ZERO);

        int neededTempValue = (int)(cfg.neededTemperature * 10) - 360;
        int neededHumidValue = (int)(cfg.neededHumidity);

        byte er = ER_ZERO;
        er  = state.overheat ? ER_OVERHEAT : ER_ZERO;
        er |= (state.chamber == IncubatorState.CHAMBER_ERROR)
                ? ER_CHAMBER_ERROR : ER_ZERO;
        er |= (!state.internet || !state.isCorrect() || !cfg.isCorrect())
                ? ER_NO_INTERNET : ER_ZERO;

        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(state.timestamp);
        bb.putShort(curTemp);
        bb.putShort(curHumid);
        bb.put(st);
        bb.put((byte)(neededTempValue & 0xFF));
        bb.put((byte)(neededHumidValue & 0xFF));
        bb.put(er);

        return bb.array();
    }

    public ArchiveRecord getArchiveRecordFromBytes(byte[] buf) {
        ArchiveRecord record = new ArchiveRecord();

        record.timestamp = ByteBuffer.wrap(buf, TIMESTAMP, TIMESTAMP_LEN).getLong();
        record.currentTemperature
                = ByteBuffer.wrap(buf, CUR_TEMP, CUR_TEMP_LEN).getShort() / 256.0f;
        record.currentHumidity
                = ByteBuffer.wrap(buf, CUR_HUMID, CUR_HUMID_LEN).getShort() / 256.0f;
        record.neededTemperature
                = ((float) buf[NEEDED_TEMP] + 360) / 10.0f;
        record.neededHumidity = buf[NEEDED_HUMID];

        record.heater = ((buf[ST] & ST_HEATER) == ST_HEATER) ? 1 : 0;
        record.wetter = ((buf[ST] & ST_WETTER) == ST_WETTER) ? 1 : 0;

        switch (buf[ST] & ST_CHAMBER) {
            case ST_CHAMBER_LEFT:
                record.chamber = IncubatorState.CHAMBER_LEFT;
                break;
            case ST_CHAMBER_NEUTRAL:
                record.chamber = IncubatorState.CHAMBER_NEUTRAL;
                break;
            case ST_CHAMBER_UNDEF:
            case ST_CHAMBER_RIGHT:
                record.chamber = IncubatorState.CHAMBER_RIGHT;
                break;
            case ST_CHAMBER_ERROR:
                record.chamber = IncubatorState.CHAMBER_ERROR;
                break;
        }

        return record;
    }

    public void writeToArchive(IncubatorState state, IncubatorConfig cfg) throws IOException {
        File archive = getLocalArchiveFile();

        PrintStream ps = new PrintStream(
                new FileOutputStream(archive, true)
        );
        ps.write(getLocalArchiveRecord(state, cfg));
    }
}
