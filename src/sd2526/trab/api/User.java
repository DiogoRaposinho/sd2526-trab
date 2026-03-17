package sd2526.trab.api;

import java.util.Set;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;

/**
 * Represents a user in the system.
 */
@Entity
public class User {

    @Id
    private String name;
    private String pwd;
    private String domain;
    private String displayName;

    @ElementCollection(fetch = FetchType.EAGER)
    private Set<String> phoneNumbers;

    public User() {
        this.pwd = null;
        this.name = null;
        this.domain = null;
        this.displayName = null;
    }

    public User(String name, String pwd, String displayName, String domain) {
        this.pwd = pwd;
        this.name = name;
        this.domain = domain;
        this.displayName = displayName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setPhoneNumbers(Set<String> phoneNumbers) {
        this.phoneNumbers = phoneNumbers;
    }

    @Override
    public String toString() {
        return "User{" +
                "name='" + name + '\'' +
                ", pwd='" + pwd + '\'' +
                ", displayName='" + displayName + '\'' +
                ", domain='" + domain + '\'' +
                '}';
    }
}
