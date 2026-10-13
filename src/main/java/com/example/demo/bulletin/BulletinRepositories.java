package com.example.demo.bulletin;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface BulletinChannelRepository extends JpaRepository<BulletinChannel, Long> {

	List<BulletinChannel> findAllByOrderByPositionAscIdAsc();

	Optional<BulletinChannel> findByHandleIgnoreCase(String handle);
}

interface BulletinRepository extends JpaRepository<Bulletin, Long> {

	Optional<Bulletin> findByDate(LocalDate date);

	Optional<Bulletin> findFirstByOrderByDateDesc();

	@Query("select b.date from Bulletin b order by b.date desc")
	List<LocalDate> findAllDates();
}

interface ChannelDigestRepository extends JpaRepository<ChannelDigest, Long> {

	List<ChannelDigest> findByChannelId(Long channelId);
}

interface ChannelSyncStateRepository extends JpaRepository<ChannelSyncState, Long> {
}
