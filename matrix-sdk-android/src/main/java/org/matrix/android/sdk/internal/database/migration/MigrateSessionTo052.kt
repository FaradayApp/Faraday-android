/*
 * Copyright (c) 2023 The Matrix.org Foundation C.I.C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.android.sdk.internal.database.migration

import io.realm.DynamicRealm
import org.matrix.android.sdk.internal.database.model.EventEntityFields
import org.matrix.android.sdk.internal.database.model.SyncEntity
import org.matrix.android.sdk.internal.database.model.SyncEntityFields
import org.matrix.android.sdk.internal.util.database.RealmMigrator

internal class MigrateSessionTo052(realm: DynamicRealm) : RealmMigrator(realm, 52) {

    override fun doMigrate(realm: DynamicRealm) {
        realm.schema.get("SyncEntity")
                ?.removeField(SyncEntityFields.ID)
                ?.addField(SyncEntityFields.USER_ID, String::class.java)
                ?.addPrimaryKey(SyncEntityFields.USER_ID)
                ?.addField(EventEntityFields.IS_VERIFICATION_STATE_DIRTY, Boolean::class.java)
                ?.setNullable(EventEntityFields.IS_VERIFICATION_STATE_DIRTY, true)
        realm.delete("SyncEntity")

    }
}
