package com.tsinghua.sample.utils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HeartRateViewModel extends ViewModel {
    private MutableLiveData<Double> respirationRate = new MutableLiveData<>();
    private MutableLiveData<Double> heartRate = new MutableLiveData<>();
    private MutableLiveData<Double> HPSR = new MutableLiveData<>();
    private MutableLiveData<double[]> waveform = new MutableLiveData<>();
    private MutableLiveData<String> process = new MutableLiveData<>();
    private MutableLiveData<String> navigationCommand = new MutableLiveData<>();
    private long viewModelStartTime;
    private final List<Long> heartRateTimestamps = new ArrayList<>();
    private final List<Double> heartRates = new ArrayList<>();
    private final List<Double> respirationRates = new ArrayList<>();
    private List<Double> ratios = new ArrayList<>();


    private Double previousHeartRate = null;
    private Double previousRespirationRate = null;

    private Random random = new Random();

    public HeartRateViewModel() {
        this.viewModelStartTime = System.currentTimeMillis(); // 记录 ViewModel 初始化的时间
    }

    public LiveData<Double> getHeartRate() {
        return heartRate;
    }

    public LiveData<Double> getRespirationRate() {
        return respirationRate;
    }
    public LiveData<Double> getHPSR() {
        return HPSR;
    }
    public LiveData<double[]> getWaveform() {
        return waveform;
    }

    public LiveData<String> getProcess() {
        return process;
    }

    public LiveData<String> getNavigationCommand() {
        return navigationCommand;
    }

    public void setHeartRate(double hr) {
        long currentTime = System.currentTimeMillis(); // 获取当前时间戳（毫秒）

        if (previousHeartRate == null) {
            heartRate.postValue(hr);
            previousHeartRate = hr;
            heartRates.add(hr);
        } else {
            boolean prevInRange = previousHeartRate >= 60.0 && previousHeartRate <= 100.0;
            boolean currentInRange = hr >= 60.0 && hr <= 100.0;
            double maxChange = 15.0;
            if (Math.abs(hr - previousHeartRate) > maxChange) {
                if (hr > previousHeartRate) {
                    hr = previousHeartRate + maxChange;
                } else {
                    hr = previousHeartRate - maxChange;
                }
            }
            double adjustedHr = hr;

            if (prevInRange && currentInRange) {
                navigationCommand.postValue("BothInRange");
            } else if (!prevInRange && currentInRange) {
                navigationCommand.postValue("RecoveredInRange");
            } else if (prevInRange && !currentInRange) {
                adjustedHr = adjustHeartRateWithRandomChange(previousHeartRate);
                navigationCommand.postValue("OutOfRangeRandom");
            } else {
                adjustedHr = hr;
                if (isTransitionFromLowToHigh(previousHeartRate, hr)) {
                    adjustedHr = (previousHeartRate + hr) / 2.0;
                }
                navigationCommand.postValue("BothOutOfRange");
            }
            heartRate.postValue(adjustedHr);
            heartRates.add(adjustedHr);
            previousHeartRate = adjustedHr;
        }

        // 记录当前心率数据的时间戳
        heartRateTimestamps.add(currentTime);
    }

    public void setRespirationRate(double rR) {
        if(rR <= 0){
            rR = 15;
        }
        if (previousRespirationRate == null) {
            // 初始情况，设置初始呼吸率
            respirationRate.postValue(rR);
            respirationRates.add(rR);
            previousRespirationRate = rR;
        } else {
            boolean prevInRange = previousRespirationRate >= 8.0 && previousRespirationRate <= 20.0;
            boolean currentInRange = rR >= 8.0 && rR <= 20.0;
            double maxChange = 3.0;
            if (Math.abs(rR - previousRespirationRate) > maxChange) {
                if (rR > previousRespirationRate) {
                    rR = previousRespirationRate + maxChange;
                } else {
                    rR = previousRespirationRate - maxChange;
                }
            }
            double adjustedrR = rR;

            if (prevInRange && currentInRange) {
                // 呼吸率都在正常范围内，直接更新
                navigationCommand.postValue("BothInRange");
            } else if (!prevInRange && currentInRange) {
                // 之前呼吸率异常，现在恢复正常
                navigationCommand.postValue("RecoveredInRange");
            } else if (prevInRange && !currentInRange) {
                // 之前呼吸率正常，现在异常
                adjustedrR = adjustRespirationRateWithRandomChange(previousRespirationRate);
                navigationCommand.postValue("OutOfRangeRandom");
            } else {
                // 呼吸率都异常，处理
                if (isTransitionFromLowToHighRespirationRate(previousRespirationRate, rR)) {
                    adjustedrR = (previousRespirationRate + rR) / 2.0;
                }
                navigationCommand.postValue("BothOutOfRange");
            }
            respirationRate.postValue(adjustedrR);
            respirationRates.add(adjustedrR);
            previousRespirationRate = adjustedrR;
        }

    }

    public void updateRatios() {
        ratios.clear();
        int minSize = Math.min(heartRates.size(), respirationRates.size());
        for (int i = 0; i < minSize; i++) {
            if (respirationRates.get(i) != 0) {  // 避免除零错误
                ratios.add(heartRates.get(i) / respirationRates.get(i));
            } else {
                ratios.add(Double.POSITIVE_INFINITY);  // 或者其他异常处理
            }
        }
    }

//    public static double calculateHRSRPercentage(double heartRate, double respRate) {
//        if (respRate == 0) {
//            throw new IllegalArgumentException("呼吸频率不能为 0");
//        }
//
//        double hrsr = heartRate / respRate;  // 计算心率与呼吸频率的比率
//        double syncPercentage = 0;
//
//        // 计算与 4 的差的绝对值
//        double diffFrom4 = Math.abs(hrsr - 4);
//
//        // 如果 hr_srs 在 0 到 3.6 之间，百分比从 0 到 80，越接近 4 百分比越高
//        if (hrsr >= 0 && hrsr < 3.6) {
//            // 根据与 4 的差异计算百分比，距离越远百分比越低
//            syncPercentage = 80 - (diffFrom4 * (80 - 0) / 3.6);  // 基于与 4 的差计算百分比
//        }
//        // 如果 hr_srs 在 3.6 到 4.4 之间，百分比从 80 到 100，越接近 4 百分比越高
//        else if (hrsr >= 3.6 && hrsr <= 4.4) {
//            syncPercentage = 80 + (diffFrom4 * (100 - 80) / 0.8);  // 基于与 4 的差计算百分比
//        }
//        // 如果 hr_srs 在 4.4 到 8 之间，百分比从 0 到 80，越接近 4 百分比越高
//        else if (hrsr > 4.4 && hrsr <= 8) {
//            syncPercentage = 80 - (diffFrom4 * (80 - 0) / 3.6);  // 基于与 4 的差计算百分比
//        }
//
//        // 确保百分比在 0% 到 100% 之间
//        syncPercentage = Math.max(0, Math.min(syncPercentage, 100));
//        return syncPercentage;
//    }


    private double adjustHeartRateWithRandomChange(double previousHr) {
        double randomChange = random.nextDouble() * 10.0;
        // 随机决定是增加还是减少
        boolean increase = random.nextBoolean();
        if (increase) {
            return previousHr + randomChange;
        } else {
            return previousHr - randomChange;
        }
    }

    private boolean isTransitionFromLowToHigh(double previousHr, double currentHr) {
        return (previousHr >= 0.0 && previousHr < 60.0) && (currentHr > 120.0);
    }
    private boolean isTransitionFromLowToHighRespirationRate(double previousRr, double currentRr){
        return (previousRr >= 0.0 && previousRr < 8.0) && (currentRr > 20.0);

    }
    public void setWaveform(double[] waveformData) {
        waveform.postValue(waveformData);
    }

    public void setProcess(String processNow) {
        process.postValue(processNow);
    }

    // 获取所有心率数据的绝对时间戳（毫秒）
    public List<Long> getHeartRateTimestamps() {
        return new ArrayList<>(heartRateTimestamps);
    }

    // 获取每个心率数据相对 ViewModel 初始化的时间（秒）
    public double[] getElapsedTimes() {
        double[] elapsedTimes = new double[heartRateTimestamps.size()];
        for (int i = 0; i < heartRateTimestamps.size(); i++) {
            elapsedTimes[i] = (heartRateTimestamps.get(i) - viewModelStartTime) / 1000.0;
        }
        return elapsedTimes;
    }

    public double[] getHeartRates() {
        double[] rates = new double[heartRates.size()];
        for (int i = 0; i < heartRates.size(); i++) {
            rates[i] = heartRates.get(i);
        }
        return rates;
    }

    public double[] getRespirationRates() {
        double[] rates = new double[respirationRates.size()];
        for (int i = 0; i < respirationRates.size(); i++) {
            rates[i] = respirationRates.get(i);
        }
        return rates;
    }
    private double adjustRespirationRateWithRandomChange(double previousRespirationRate) {
        double randomChange = random.nextDouble() * 3.0;
        boolean increase = random.nextBoolean();  // 随机增加或减少
        if (increase) {
            return previousRespirationRate + randomChange;
        } else {
            return previousRespirationRate - randomChange;
        }
    }
    public void resetTime(){
        this.viewModelStartTime = System.currentTimeMillis(); // 记录 ViewModel 初始化的时间

    }
    public void resetVariables() {

        heartRateTimestamps.clear();
        heartRates.clear();
        respirationRates.clear();
        ratios.clear();


        previousHeartRate = null;
        previousRespirationRate = null;


    }


}
