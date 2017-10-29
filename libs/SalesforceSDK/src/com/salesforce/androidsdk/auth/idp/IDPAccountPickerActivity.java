/*
 * Copyright (c) 2017-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.auth.idp;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.app.SalesforceSDKManager;
import com.salesforce.androidsdk.ui.AccountSwitcherActivity;
import com.salesforce.androidsdk.util.SalesforceSDKLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides UI to select an existing signed in user account, or add a new account.
 * It kicks off the IDP authentication flow once the selection is made. This screen is popped
 * off the activity stack once the flow is complete, passing back control to the SP app.
 *
 * @author bhariharan
 */
public class IDPAccountPickerActivity extends AccountSwitcherActivity {

    private static final int IDP_LOGIN_REQUEST_CODE = 999;
    private static final String TAG = "IDPAccountPickerActivity";

    private SPConfig spConfig;

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        // Fetches the required extras.
        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();
        if (extras != null) {
            spConfig = new SPConfig(extras.getBundle(IDPCodeGeneratorActivity.SP_CONFIG_BUNDLE_KEY));
        }
    }

    @Override
    protected List<UserAccount> getAccounts() {
        final List<UserAccount> accounts = userAccMgr.getAuthenticatedUsers();

        // If no login server is passed in, return all user accounts.
        if (TextUtils.isEmpty(spConfig.getLoginUrl())) {
            return accounts;
        }
        final List<UserAccount> filteredAccounts = new ArrayList<>();
        if (accounts != null) {
            for (final UserAccount account : accounts) {

                // If user account has the same login server, add it to the list.
                if (spConfig.getLoginUrl().equals(account.getLoginServer())) {
                    filteredAccounts.add(account);
                }
            }
        }
        if (filteredAccounts.size() == 0) {
            return null;
        }
        return filteredAccounts;
    }

    @Override
    protected void accountSelected(UserAccount account) {
        SalesforceSDKLogger.d(TAG, "Account selected: " + account);

        /*
         * If an account is passed in, proceeds with IDP auth flow directly. This will kick off
         * authentication for the SP app without messing with the state of the IDP. If no account
         * is passed in, kicks off new user login flow, sets current user on the IDP to the newly
         * logged in user, and then resumes authentication for the SP app with that user.
         */
        if (account != null) {
            proceedWithIDPAuthFlow(account);
        } else {
            kickOffNewUserFlow();
        }
    }

    @Override
    protected void finishActivity() {

        // Do nothing in here, since we don't want to finish this activity until the IDP flow is complete.
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IDP_LOGIN_REQUEST_CODE) {
            SalesforceSDKLogger.d(TAG, "Activity result obtained - IDP login complete");

            // Kicks off the SP app's authentication call, since the IDP app is now authenticated.
            // TODO: Replace the following line with account returned from LoginActivity instead.
            final UserAccount account = userAccMgr.getCurrentUser();
            if (account != null) {
                proceedWithIDPAuthFlow(account);
            }
        } else  if (requestCode == SPRequestHandler.IDP_REQUEST_CODE) {
            SalesforceSDKLogger.d(TAG, "Activity result obtained - IDP code exchange complete");
            if (resultCode == Activity.RESULT_OK) {
                setResult(RESULT_OK, data);
            } else {
                setResult(RESULT_CANCELED, data);
            }
            finish();
        }
    }

    private void kickOffNewUserFlow() {
        SalesforceSDKLogger.d(TAG, "Kicking off new user flow within IDP");
        final Bundle reply = new Bundle();
        final Bundle options = SalesforceSDKManager.getInstance().getLoginOptions().asBundle();
        final Intent i = new Intent(this, SalesforceSDKManager.getInstance().getLoginActivityClass());
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtras(options);
        reply.putParcelable(AccountManager.KEY_INTENT, i);
        startActivityForResult(i, IDP_LOGIN_REQUEST_CODE);
    }

    private void proceedWithIDPAuthFlow(UserAccount account) {
        SalesforceSDKLogger.d(TAG, "Kicking off code exchange flow within IDP for account: " + account);
        final Intent intent = new Intent(this, IDPCodeGeneratorActivity.class);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.putExtra(IDPCodeGeneratorActivity.SP_CONFIG_BUNDLE_KEY, spConfig.toBundle());
        intent.putExtra(IDPCodeGeneratorActivity.USER_ACCOUNT_BUNDLE_KEY, account.toBundle());
        startActivityForResult(intent, SPRequestHandler.IDP_REQUEST_CODE);
    }
}
