package com.example.bitcoinqrscannerforbtcbchbsvbtg;

/*-
 * -----------------------LICENSE_START-----------------------
 * QR scanner for bitcoin, bitcoin cash, bitcoin sv and bitcoin gold
 * %%
 * Copyright (C) 2020 Johan MEERT
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -----------------------LICENSE_END-----------------------
 */

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Bech32;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static com.example.bitcoinqrscannerforbtcbchbsvbtg.BitcoinAddressValidator.validateBitcoinAddress;
import static com.example.bitcoinqrscannerforbtcbchbsvbtg.BitcoinCashAddressConverter.toCashAddress;
import static com.example.bitcoinqrscannerforbtcbchbsvbtg.BitcoinCashAddressConverter.toLegacyAddress;
import static com.example.bitcoinqrscannerforbtcbchbsvbtg.BitcoinGoldAddressConverter.BTCtoBTG;
import static com.example.bitcoinqrscannerforbtcbchbsvbtg.BitcoinGoldAddressConverter.BTGtoBTC;

public class MainActivity extends AppCompatActivity {

    public static final int REQUEST_CODE_QR = 42574;  // random number
    static final String BTC_TEST_ADDRESS = "15VEJBFyCBSFpnkxiLZXPCoo2x72ACwHpt";  // test address, should contain 10 BTC :)
    EditText bitcoinAddressPreviewEditText;
    TextView statusTextView;
    TextView btcPreviewTextView, bchPreviewTextView, bsvPreviewTextView, btgPreviewTextView;
    TextView btcValueTextView, bchValueTextView, bsvValueTextView, btgValueTextView;
    Button getValueButton, copyBtcButton, copyBchButton, copyBsvButton, copyBtgButton;

    enum BtcAddressStatus {INVALID, BTCFORMAT, BECH32FORMAT}

    ;
    static final BigDecimal MINUS_ONE = BigDecimal.valueOf(-1.0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bitcoinAddressPreviewEditText = findViewById(R.id.editTextQRCode);
        statusTextView = findViewById(R.id.textViewStatus);
        btcPreviewTextView = findViewById(R.id.textViewBTCaddress);
        bchPreviewTextView = findViewById(R.id.textViewBCHaddress);
        bsvPreviewTextView = findViewById(R.id.textViewBSVaddress);
        btgPreviewTextView = findViewById(R.id.textViewBTGaddress);
        btcValueTextView = findViewById(R.id.textViewBTCvalue);
        bchValueTextView = findViewById(R.id.textViewBCHvalue);
        bsvValueTextView = findViewById(R.id.textViewBSVvalue);
        btgValueTextView = findViewById(R.id.textViewBTGvalue);
        bitcoinAddressPreviewEditText.setText(BTC_TEST_ADDRESS);
        getValueButton = findViewById(R.id.buttonGetValue);
        copyBtcButton = findViewById(R.id.buttonCopyBTC);
        copyBchButton = findViewById(R.id.buttonCopyBCH);
        copyBsvButton = findViewById(R.id.buttonCopyBSV);
        copyBtgButton = findViewById(R.id.buttonCopyBTG);
    }

    public void OnClickScan(View V) {
        Intent intent = new Intent(this, Qrscanner.class);
        startActivityForResult(intent, REQUEST_CODE_QR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == REQUEST_CODE_QR) && (resultCode == Activity.RESULT_OK)) { // activity return from QR scanner
            String result = data.getStringExtra("QR");
            if (result != null) {
                Log.i("QR_code_returned: ", result);
                bitcoinAddressPreviewEditText.setText(result);
                statusTextView.setText("QR code successfully scanned");
                statusTextView.setTextColor(Color.GREEN);
                activateAllCopyButtons();
            }
        }
    }

    public void OnClickPaste(View V) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if ((clipboard.hasPrimaryClip()) && (clipboard.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN))) {  // clipboard not empty & contains text
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            String pasteAddress = item.getText().toString();
            if (pasteAddress != null) {
                bitcoinAddressPreviewEditText.setText(pasteAddress);
                statusTextView.setText("Successful paste from clipboard");
                statusTextView.setTextColor(Color.GREEN);
            } else {
                statusTextView.setText("Clipboard text invalid");
                statusTextView.setTextColor(Color.RED);
            }
        } else {
            statusTextView.setText("Clipboard empty or does not contain text");
            statusTextView.setTextColor(Color.RED);
        }
    }

    public void OnClickCheckAddress(View V) {
        String testAddress = bitcoinAddressPreviewEditText.getText().toString();
        if (simpleAddressIsValid(testAddress)) {
            if (isBTGAddress(testAddress)) {
                statusTextView.setText("This is a valid BTG address");
                statusTextView.setTextColor(Color.GREEN);
                String legacyAddress = BTGtoBTC(testAddress);
                btcPreviewTextView.setText(legacyAddress);
                bchPreviewTextView.setText(toCashAddress(legacyAddress));
                bsvPreviewTextView.setText(legacyAddress);
                btgPreviewTextView.setText(testAddress);
            } else if (isBTCAddress(testAddress) == BtcAddressStatus.BTCFORMAT) {
                statusTextView.setText("This is a valid BTC/BSV address");
                statusTextView.setTextColor(Color.GREEN);
                btcPreviewTextView.setText(testAddress);
                bchPreviewTextView.setText(toCashAddress(testAddress));
                bsvPreviewTextView.setText(testAddress);
                btgPreviewTextView.setText(BTCtoBTG(testAddress));
            } else if (isBTCAddress(testAddress) == BtcAddressStatus.BECH32FORMAT) {
                statusTextView.setText("This is a valid Bech32 address");
                statusTextView.setTextColor(Color.GREEN);
                testAddress = testAddress.toLowerCase();
                btcPreviewTextView.setText(testAddress);
                btgPreviewTextView.setText(testAddress);
                bchPreviewTextView.setText(testAddress);
                bsvPreviewTextView.setText(testAddress);
            } else if (isBCHAddress(testAddress)) {
                statusTextView.setText("This is a valid BCH address");
                statusTextView.setTextColor(Color.GREEN);
                String legacyAddress = toLegacyAddress(testAddress);
                btcPreviewTextView.setText(legacyAddress);
                bchPreviewTextView.setText(testAddress);
                bsvPreviewTextView.setText(legacyAddress);
                btgPreviewTextView.setText(BTCtoBTG(legacyAddress));
            } else {
                statusTextView.setText("Not a valid BTC/BCH/BSV/BTG address");
                statusTextView.setTextColor(Color.RED);
                return;
            }
            getValueButton.setEnabled(true);
            activateAllCopyButtons();
        }
    }

    public void OnClickGetValue(View V) {
        // getting the value means doing http network requests
        // these are prohibited to run in the Android GUI
        // so they are programmed in private classes extending AsyncTask
        new GetBtcFromWeb().execute(btcPreviewTextView.getText().toString());
        new GetBchFromWeb().execute(btcPreviewTextView.getText().toString());
        new GetBsvFromWeb().execute(btcPreviewTextView.getText().toString());
        new GetBtgFromWeb().execute(btgPreviewTextView.getText().toString());
    }

    public void OnClickClear(View V) {
        bitcoinAddressPreviewEditText.setText("");
        statusTextView.setText("Preview cleared");
        statusTextView.setTextColor(Color.GREEN);
        getValueButton.setEnabled(false);
        btcPreviewTextView.setText("");
        bchPreviewTextView.setText("");
        bsvPreviewTextView.setText("");
        btgPreviewTextView.setText("");
        btcValueTextView.setText("none");
        bchValueTextView.setText("none");
        bsvValueTextView.setText("none");
        btgValueTextView.setText("none");
        deactivateAllCopyButtons();
    }

    public void OnClickCopyBTC(View V) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("btc address", btcPreviewTextView.getText().toString());
        clipboard.setPrimaryClip(clip);
        statusTextView.setText("Succeful copy to clipboard");
        statusTextView.setTextColor(Color.GREEN);
    }

    public void OnClickCopyBCH(View V) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("btc address", bchPreviewTextView.getText().toString());
        clipboard.setPrimaryClip(clip);
        statusTextView.setText("Succeful copy to clipboard");
        statusTextView.setTextColor(Color.GREEN);
    }

    public void OnClickCopyBSV(View V) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("btc address", bsvPreviewTextView.getText().toString());
        clipboard.setPrimaryClip(clip);
        statusTextView.setText("Succeful copy to clipboard");
        statusTextView.setTextColor(Color.GREEN);
    }

    public void OnClickCopyBTG(View V) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("btc address", btgPreviewTextView.getText().toString());
        clipboard.setPrimaryClip(clip);
        statusTextView.setText("Succeful copy to clipboard");
        statusTextView.setTextColor(Color.GREEN);
    }

    public void OnClickQuit(View V) {
        finish();
        System.exit(0);
    }


    // all other Methods from here

    private boolean simpleAddressIsValid(String address) {
        // check 1: IsEmpty ?
        if (bitcoinAddressPreviewEditText.getText().toString().isEmpty()) {
            statusTextView.setText("Address preview is empty");
            statusTextView.setTextColor(Color.RED);
            return false;
        } else {
            address = address.trim();  // remove spaces
            bitcoinAddressPreviewEditText.setText(address);
        }
        // check 2: contains illegal characters ?
        if (!address.matches("-?[:0-9a-zA-HJ-NP-Z]+")) {
            // can contain only base58 (base64 without 0lIO) and also colon ':' for 'bitcoincash:address', 0 is also included because of bitcoin cash, bech32 address compatible
            statusTextView.setText("Illegal character in address");
            statusTextView.setTextColor(Color.RED);
            return false;
        }
        if (address.contains(":")) {
            if (!address.contains("bitcoincash:")) {
                statusTextView.setText("colon character only allowed with bitcoincash:");
                statusTextView.setTextColor(Color.RED);
                return false;
            }
        }
        statusTextView.setText("Simple address check ok");
        statusTextView.setTextColor(Color.GREEN);
        return true;
    }

    private BtcAddressStatus isBTCAddress(String address) {
        if (validateBitcoinAddress(address))
            return BtcAddressStatus.BTCFORMAT;  // check for classic bitcoin address
        try {
            Bech32.Bech32Data b32d = Bech32.decode(address);  // check for bech32 address
            return BtcAddressStatus.BECH32FORMAT;
        } catch (AddressFormatException.InvalidCharacter | AddressFormatException.InvalidDataLength | AddressFormatException.InvalidPrefix | AddressFormatException.InvalidChecksum ae) {
            Log.e("Bech32 conversion:", ae.toString());
        }
        return BtcAddressStatus.INVALID;
    }

    private boolean isBCHAddress(String address) {
        if (address.length() != 54) return false;
        if (!address.contains("bitcoincash:")) return false;
        if (!address.matches("-?[:02-9a-z]+")) return false;
        return true;
    }

    private boolean isBTGAddress(String address) {
        if ((address.charAt(0) != 'G') && (address.charAt(0) != 'A')) return false;
        return true;
    }


    private void activateAllCopyButtons() {
        copyBtcButton.setEnabled(true);
        copyBchButton.setEnabled(true);
        copyBsvButton.setEnabled(true);
        copyBtgButton.setEnabled(true);
    }

    private void deactivateAllCopyButtons() {
        copyBtcButton.setEnabled(false);
        copyBchButton.setEnabled(false);
        copyBsvButton.setEnabled(false);
        copyBtgButton.setEnabled(false);
    }

    private class GetBtcFromWeb extends AsyncTask<String, Void, BigDecimal> {

        @Override
        protected BigDecimal doInBackground(String... strings) {
            String checkAddress = strings[0];
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                URL url = new URL("https://blockchain.info/balance?active=" + checkAddress);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                InputStream stream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(stream));
                StringBuffer buffer = new StringBuffer();
                String line = "";
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }
                try {
                    JsonObject deserialize = (JsonObject) Jsoner.deserialize(buffer.toString());
                    JsonObject deserialize2 = (JsonObject) deserialize.get(checkAddress);
                    BigDecimal finalvalue = (BigDecimal) deserialize2.get("final_balance");
                    BigDecimal finalValue = finalvalue.divide(BigDecimal.valueOf(100000000));
                    return finalValue;
                } catch (JsonException e) {
                    e.printStackTrace();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return MINUS_ONE;
        }

        @Override
        protected void onPostExecute(BigDecimal bigDecimal) {
            super.onPostExecute(bigDecimal);
            if (bigDecimal.equals(MINUS_ONE)) {
                btcValueTextView.setText("Error in lookup");
            } else {
                btcValueTextView.setText(bigDecimal.toString());
            }
        }
    }

    private class GetBchFromWeb extends AsyncTask<String, Void, BigDecimal> {

        @Override
        protected BigDecimal doInBackground(String... strings) {
            String checkAddress = strings[0];
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                URL url = new URL("https://bch-chain.api.btc.com/v3/address/" + checkAddress);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                InputStream stream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(stream));
                StringBuffer buffer = new StringBuffer();
                String line = "";
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }
                try {
                    JsonObject deserialize = (JsonObject) Jsoner.deserialize(buffer.toString());
                    JsonObject deserialize2 = (JsonObject) deserialize.get("data");
                    if (deserialize2 == null) return BigDecimal.valueOf(0);
                    BigDecimal finalvalue = (BigDecimal) deserialize2.get("balance");
                    BigDecimal finalValue = finalvalue.divide(BigDecimal.valueOf(100000000));
                    return finalValue;
                } catch (JsonException e) {
                    e.printStackTrace();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return MINUS_ONE;
        }

        @Override
        protected void onPostExecute(BigDecimal bigDecimal) {
            super.onPostExecute(bigDecimal);
            if (bigDecimal.equals(MINUS_ONE)) {
                bchValueTextView.setText("Error in lookup");
            } else {
                bchValueTextView.setText(bigDecimal.toString());
            }
        }
    }

    private class GetBsvFromWeb extends AsyncTask<String, Void, BigDecimal> {

        @Override
        protected BigDecimal doInBackground(String... strings) {
            String checkAddress = strings[0];
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                URL url = new URL("https://api.whatsonchain.com/v1/bsv/main/address/" + checkAddress + "/balance");
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                InputStream stream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(stream));
                StringBuffer buffer = new StringBuffer();
                String line = "";
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }
                try {
                    JsonObject deserialize = (JsonObject) Jsoner.deserialize(buffer.toString());
                    BigDecimal finalvalue = (BigDecimal) deserialize.get("confirmed");
                    BigDecimal finalValue = finalvalue.divide(BigDecimal.valueOf(100000000));
                    return finalValue;
                } catch (JsonException e) {
                    e.printStackTrace();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return MINUS_ONE;
        }

        @Override
        protected void onPostExecute(BigDecimal bigDecimal) {
            super.onPostExecute(bigDecimal);
            if (bigDecimal.equals(MINUS_ONE)) {
                bsvValueTextView.setText("Error in lookup");
            } else {
                bsvValueTextView.setText(bigDecimal.toString());
            }
        }
    }


    private class GetBtgFromWeb extends AsyncTask<String, Void, BigDecimal> {

        @Override
        protected BigDecimal doInBackground(String... strings) {
            String checkAddress = strings[0];
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                URL url = new URL("https://explorer.bitcoingold.org/insight-api/addr/" + checkAddress);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                InputStream stream = connection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(stream));
                StringBuffer buffer = new StringBuffer();
                String line = "";
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }
                try {
                    JsonObject deserialize = (JsonObject) Jsoner.deserialize(buffer.toString());
                    BigDecimal finalValue = (BigDecimal) deserialize.get("balance");
                    return finalValue;
                } catch (JsonException e) {
                    e.printStackTrace();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return MINUS_ONE;
        }

        @Override
        protected void onPostExecute(BigDecimal bigDecimal) {
            super.onPostExecute(bigDecimal);
            if (bigDecimal.equals(MINUS_ONE)) {
                btgValueTextView.setText("Error in lookup");
            } else {
                btgValueTextView.setText(bigDecimal.toString());
            }
        }
    }


}
