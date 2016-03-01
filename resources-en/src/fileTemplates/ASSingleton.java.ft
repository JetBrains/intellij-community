#if (${PACKAGE_NAME} != "")package ${PACKAGE_NAME};#end

#if (${IMPORT_BLOCK} != "")${IMPORT_BLOCK}
#end
#parse("File Header.java")

#if (${VISIBILITY} == "PUBLIC")public #end class ${NAME} #if (${SUPERCLASS} != "")extends ${SUPERCLASS} #end #if (${INTERFACES} != "")implements ${INTERFACES} #end {
    private static final ${NAME} ourInstance = new ${NAME}();

    #if (${VISIBILITY} == "PUBLIC")public #end static ${NAME} getInstance() {
        return ourInstance;
    }

    private ${NAME}() {
    }
}
