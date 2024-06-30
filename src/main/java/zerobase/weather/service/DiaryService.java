package zerobase.weather.service;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import zerobase.weather.WeatherApplication;
import zerobase.weather.domain.DateWeather;
import zerobase.weather.domain.Diary;
import zerobase.weather.error.InvalidDate;
import zerobase.weather.repository.DateWeatherRepository;
import zerobase.weather.repository.DiaryRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class DiaryService {

    // API 인증키
    @Value("${openweathermap.key}")
    private String apiKey;

    final private DiaryRepository diaryRepository;

    final private DateWeatherRepository dateWeatherRepository;

    final private Logger logger = LoggerFactory.getLogger(WeatherApplication.class);

    public DiaryService(DiaryRepository diaryRepository, DateWeatherRepository dateWeatherRepository) {
        this.diaryRepository = diaryRepository;
        this.dateWeatherRepository = dateWeatherRepository;
    }

    /**
     * 새벽 1시마다 날씨 데이터 가져옴
     */
    @Transactional
//    @Scheduled(cron = "0/5 * * * * *") // 5초에 한번
    @Scheduled(cron = "0 0 1 * * *")
    public void saveWeatherData(){
        logger.info("[새벽1시] 날씨 데이터 적재 성공");
        dateWeatherRepository.save(getWeatherFromApi());
        
    }

    /**
     * 날씨 일기 생성 - API 호출
     */
    private DateWeather getWeatherFromApi(){
        // 1. open weather map 에서 날씨 데이터 가져오기
        String weatherData = getWeatherString();
        System.out.println(weatherData);

        // 2. 받아온 날씨 json 파싱하기
        Map<String, Object> parsedWeather = parseWeather(weatherData);

        // 3. DateWeather 타입으로 변환하여 반환
        DateWeather dateWeather = new DateWeather();
        dateWeather.setDate(LocalDate.now()); // 처리일자
        dateWeather.setWeather(parsedWeather.get("main").toString());
        dateWeather.setIcon(parsedWeather.get("icon").toString());
        dateWeather.setTemperature((double) parsedWeather.get("temp"));

        return dateWeather;
    }

    /**
     * 날씨 일기 생성
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void createDiary(LocalDate date, String text){
        logger.info("started to create diary");
        // 날씨 데이터 가져오기 (api 또는 db)
        DateWeather dateWeather = getDateWeather(date);

        // 일기데이터 저장
        Diary nowDiary = new Diary();
        nowDiary.setDateWeather(dateWeather);
        nowDiary.setText(text);
        diaryRepository.save(nowDiary);
        logger.info("end to create diary");

    }

    /**
     * 날씨 일기 생성 - API 호출 OR DB에서 조회할지 판단 (캐싱)
     */
    private DateWeather getDateWeather(LocalDate date) {
        List<DateWeather> dateWeatherListFromDB = dateWeatherRepository.findAllByDate(date);
        if(dateWeatherListFromDB.size() == 0) {
            // 새로 api에서 날씨 정보를 가져온다
            System.out.println("새로 api에서 데이터 조회 ");
            return getWeatherFromApi();
        } else {
            System.out.println("있는 데이터라서 디비에서 조회 ");
            return dateWeatherListFromDB.get(0);
        }
    }


    /**
     * 날씨 일기 조회
     */
    @Transactional(readOnly = true)
    public List<Diary> readDiary(LocalDate date){


        logger.debug("read diary");
//        if(date.isAfter(LocalDate.ofYearDay(3050, 1))) {
//            throw new InvalidDate();
//        }

        return diaryRepository.findAllByDate(date);
    }

    /**
     * 날씨 일기 조회 (시작, 종료일 기간으로 조회)
     */
    @Transactional(readOnly = true)
    public List<Diary> readDiaries(LocalDate startDate, LocalDate endDate){
        return diaryRepository.findAllByDateBetween(startDate, endDate);
    }


    /**
     * 날씨 일기 수정
     */
    public void updateDiary(LocalDate date, String text) {
        Diary nowDiary = diaryRepository.getFirstByDate(date);
        nowDiary.setText(text);
        diaryRepository.save(nowDiary);  // id값은 그대로 인채로 다른 컬럼만 변경하였기 때문에 update 처리된다
    }

    /**
     * 날씨 일기 삭제
     */
    public void deleteDiary(LocalDate date) {
        diaryRepository.deleteAllByDate(date);
    }

    
    /**
     * 날씨 일기 생성 - API 호출
     */
    public String getWeatherString(){
        String apiUrl = "https://api.openweathermap.org/data/2.5/weather?q=seoul&appId=" + apiKey;
        System.out.println(apiUrl);

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            BufferedReader br;
            if(responseCode == 200){
                // 정상
                br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            } else {
                // 실패
                br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
            }

            String inputLine;
            StringBuilder response = new StringBuilder();
            while((inputLine = br.readLine()) != null){
                response.append(inputLine);
            }

            br.close();

            return response.toString();

        } catch(Exception e) {
            return "failed to get response";
        }

    }

    /**
     * 날씨 일기 생성 - API 응답데이터 파싱
     */
    private HashMap<String, Object> parseWeather(String jsonString) {
        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObject;

        try {
            jsonObject = (JSONObject) jsonParser.parse(jsonString);

        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        HashMap<String, Object> resultMap = new HashMap<>();
        JSONObject mainData = (JSONObject) jsonObject.get("main");
        resultMap.put("temp", mainData.get("temp"));

        // [{}] 형태로 넘어옴 !
        JSONArray weatherArray = (JSONArray)jsonObject.get("weather");
        if (weatherArray != null && !weatherArray.isEmpty()) {
            JSONObject weatherData = (JSONObject) weatherArray.get(0);
            resultMap.put("main", (String) weatherData.get("main"));;
            resultMap.put("icon", (String) weatherData.get("icon"));
        }

        return resultMap;
    }
}
