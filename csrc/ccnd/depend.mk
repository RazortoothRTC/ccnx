ccnd_main.o: ccnd_main.c ccnd_private.h ../include/ccn/ccn_private.h \
  ../include/ccn/coding.h ../include/ccn/nametree.h \
  ../include/ccn/reg_mgmt.h ../include/ccn/charbuf.h \
  ../include/ccn/schedule.h ../include/ccn/seqwriter.h ccnd_strategy.h
ccnd.o: ccnd.c ../include/ccn/bloom.h ../include/ccn/ccn.h \
  ../include/ccn/coding.h ../include/ccn/charbuf.h \
  ../include/ccn/indexbuf.h ../include/ccn/ccn_private.h \
  ../include/ccn/ccnd.h ../include/ccn/face_mgmt.h \
  ../include/ccn/sockcreate.h ../include/ccn/flatname.h \
  ../include/ccn/hashtb.h ../include/ccn/nametree.h \
  ../include/ccn/schedule.h ../include/ccn/reg_mgmt.h \
  ../include/ccn/strategy_mgmt.h ../include/ccn/uri.h ccnd_private.h \
  ../include/ccn/seqwriter.h ccnd_strategy.h
ccnd_msg.o: ccnd_msg.c ../include/ccn/ccn.h ../include/ccn/coding.h \
  ../include/ccn/charbuf.h ../include/ccn/indexbuf.h \
  ../include/ccn/ccnd.h ../include/ccn/flatname.h ../include/ccn/hashtb.h \
  ../include/ccn/uri.h ccnd_private.h ../include/ccn/ccn_private.h \
  ../include/ccn/nametree.h ../include/ccn/reg_mgmt.h \
  ../include/ccn/schedule.h ../include/ccn/seqwriter.h ccnd_strategy.h
ccnd_stats.o: ccnd_stats.c ../include/ccn/ccn.h ../include/ccn/coding.h \
  ../include/ccn/charbuf.h ../include/ccn/indexbuf.h \
  ../include/ccn/ccnd.h ../include/ccn/schedule.h \
  ../include/ccn/sockaddrutil.h ../include/ccn/hashtb.h \
  ../include/ccn/nametree.h ../include/ccn/uri.h ccnd_private.h \
  ../include/ccn/ccn_private.h ../include/ccn/reg_mgmt.h \
  ../include/ccn/seqwriter.h ccnd_strategy.h
ccnd_internal_client.o: ccnd_internal_client.c ../include/ccn/ccn.h \
  ../include/ccn/coding.h ../include/ccn/charbuf.h \
  ../include/ccn/indexbuf.h ../include/ccn/ccn_private.h \
  ../include/ccn/hashtb.h ../include/ccn/keystore.h \
  ../include/ccn/schedule.h ../include/ccn/sockaddrutil.h \
  ../include/ccn/uri.h ccnd_private.h ../include/ccn/nametree.h \
  ../include/ccn/reg_mgmt.h ../include/ccn/seqwriter.h ccnd_strategy.h
ccnd_stregistry.o: ccnd_stregistry.c ccnd_stregistry.h ccnd_strategy.h
ccnd_strategy0.o: ccnd_strategy0.c ccnd_strategy.h ccnd_private.h \
  ../include/ccn/ccn_private.h ../include/ccn/coding.h \
  ../include/ccn/nametree.h ../include/ccn/reg_mgmt.h \
  ../include/ccn/charbuf.h ../include/ccn/schedule.h \
  ../include/ccn/seqwriter.h
ccndsmoketest.o: ccndsmoketest.c ../include/ccn/ccnd.h \
  ../include/ccn/ccn_private.h
ccnd_strategy1.o: ccnd_strategy1.c ccnd_strategy.h
ccnd_strategy2.o: ccnd_strategy2.c ccnd_strategy.h
