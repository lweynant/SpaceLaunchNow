
package me.calebjones.spacelaunchnow.content.models.realm;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class LocationRealm extends RealmObject {

    @PrimaryKey
    private String primaryID;
    private Integer id;
    private String name;
    private RealmList<PadRealm> pads = new RealmList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RealmList<PadRealm> getPads() {
        return pads;
    }

    public void setPads(RealmList<PadRealm> pads) {
        this.pads = pads;
    }

    public void setPrimaryID(){
        if (pads != null && id != null) {
            if (pads.size() > 0) {
                this.primaryID = String.valueOf(id) + "-" + String.valueOf(pads.get(0).getId());
            }
        }
    }

}
