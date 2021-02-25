package x.sms;

import android.os.Build;
import android.support.annotation.RequiresApi;
import android.telephony.SmsManager;

/**
 * Created by matej on 3/5/2019.
 */
public class SMS {

    private String number;
    private String text;
    //private int sim;

    public SMS(String number, String text) {
        this.number = number;
        this.text = text;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    void sendSMS() {
        SmsManager sms = SmsManager.getDefault();
       // sms.getSmsManagerForSubscriptionId(this.sim).sendTextMessage(this.number, null, this.text, null, null);
        sms.sendTextMessage("phoneNo", null, this.text, null, null);
    }
}
