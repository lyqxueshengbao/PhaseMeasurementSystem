function result = Filter(signal,L,Fs,start1,end1)
Signal = fftshift(fft(signal));
Filter = zeros(1,L);
Filter(floor((start1+Fs/2)*L/Fs):floor((end1+Fs/2)*L/Fs))= 2*ones(1,floor((end1+Fs/2)*L/Fs)-floor((start1+Fs/2)*L/Fs)+1);
Filter(floor((-end1+Fs/2)*L/Fs):floor((-start1+Fs/2)*L/Fs))= 2*ones(1,floor((-start1+Fs/2)*L/Fs)-floor((-end1+Fs/2)*L/Fs)+1);

Signal2 = Signal.*Filter;
result = ifft(ifftshift(Signal2));

end