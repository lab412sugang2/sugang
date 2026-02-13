package sugang.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "courses", uniqueConstraints = @UniqueConstraint(columnNames = {"code", "division_number"}))
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(name = "division_number", nullable = false)
    private Integer divisionNumber;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Integer credit;

    @Column(nullable = false, length = 50)
    private String professor;

    @Column(nullable = false, length = 120)
    private String schedule;

    @Column(name = "limit_count", nullable = false)
    private Integer limitCount;

    @Column(name = "applied_count", nullable = false)
    private Integer appliedCount;

    @Column(name = "canceled", nullable = false)
    private boolean canceled;

    public Course() {
    }

    public Course(String code, Integer divisionNumber, String name, Integer credit, String professor, String schedule,
                  Integer limitCount, Integer appliedCount, boolean canceled) {
        this.code = code;
        this.divisionNumber = divisionNumber;
        this.name = name;
        this.credit = credit;
        this.professor = professor;
        this.schedule = schedule;
        this.limitCount = limitCount;
        this.appliedCount = appliedCount;
        this.canceled = canceled;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Integer getDivisionNumber() {
        return divisionNumber;
    }

    public String getName() {
        return name;
    }

    public Integer getCredit() {
        return credit;
    }

    public String getProfessor() {
        return professor;
    }

    public String getSchedule() {
        return schedule;
    }

    public Integer getLimitCount() {
        return limitCount;
    }

    public Integer getAppliedCount() {
        return appliedCount;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public boolean isFull() {
        return appliedCount >= limitCount;
    }

    public void increaseAppliedCount() {
        this.appliedCount = this.appliedCount + 1;
    }

    public void decreaseAppliedCount() {
        if (this.appliedCount > 0) {
            this.appliedCount = this.appliedCount - 1;
        }
    }
}
