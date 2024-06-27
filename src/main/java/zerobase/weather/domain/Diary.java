package zerobase.weather.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor

public class Diary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private String id;

    private String weather;

    private String icon;

    private double temperature;

    private LocalDate date;

    private String text;
}
